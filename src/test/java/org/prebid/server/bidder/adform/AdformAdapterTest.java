package org.prebid.server.bidder.adform;

import com.iab.openrtb.request.Device;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.Usersyncer;
import org.prebid.server.bidder.adform.model.AdformBid;
import org.prebid.server.bidder.adform.model.AdformParams;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.BDDMockito.given;

public class AdformAdapterTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://adform.com/openrtb2d";
    private static final CharSequence X_REQUEST_AGENT = HttpHeaders.createOptimized("X-Request-Agent");
    private static final CharSequence X_FORWARDED_FOR = HttpHeaders.createOptimized("X-Forwarded-For");
    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";
    private static final String BIDDER = "adform";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookie uidsCookie;

    @Mock
    private Usersyncer usersyncer;

    private AdformAdapter adformAdapter;

    @Before
    public void setUp() {
        adformAdapter = new AdformAdapter(usersyncer, ENDPOINT_URL);
        given(usersyncer.cookieFamilyName()).willReturn(BIDDER);
        given(uidsCookie.uidFrom(BIDDER)).willReturn("buyeruid");
    }

    @Test
    public void makeHttpRequestsShouldReturnAdapterHttpRequestWithCorrectUrlAndHeaders() {
        // given
        final AdapterRequest adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                AdUnitBid.builder().bidId("bidId").adUnitCode("AdUnitCode")
                        .params(Json.mapper.valueToTree(AdformParams.of(15L))).build()));
        final PreBidRequestContext preBidRequestContext = PreBidRequestContext.builder().preBidRequest(
                PreBidRequest.builder().tid("tid").device(Device.builder().ifa("ifaId").build()).build())
                .secure(0).ua("userAgent").ip("192.168.0.1").referer("www.example.com").uidsCookie(uidsCookie).build();

        // when
        final List<AdapterHttpRequest<Void>> adapterHttpRequests = adformAdapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then

        // bWlkPTE1 is Base64 encoded "mid=15" value
        assertThat(adapterHttpRequests).hasSize(1)
                .extracting(AdapterHttpRequest::getUri)
                .containsExactly(
                        "http://adform.com/openrtb2d/?CC=1&rp=4&fd=1&stid=tid&ip=192.168.0.1&adid=ifaId&bWlkPTE1");

        assertThat(adapterHttpRequests)
                .extracting(AdapterHttpRequest::getMethod)
                .containsExactly(HttpMethod.GET);

        assertThat(adapterHttpRequests)
                .extracting(AdapterHttpRequest::getPayload).containsNull();

        assertThat(adapterHttpRequests)
                .flatExtracting(adapterHttpRequest -> adapterHttpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple(HttpHeaders.CONTENT_TYPE.toString(), APPLICATION_JSON),
                        tuple(HttpHeaders.ACCEPT.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpHeaders.USER_AGENT.toString(), "userAgent"),
                        tuple(X_FORWARDED_FOR.toString(), "192.168.0.1"),
                        tuple(X_REQUEST_AGENT.toString(), "PrebidAdapter 0.1.1"),
                        tuple(HttpHeaders.REFERER.toString(), "www.example.com"),
                        tuple(HttpHeaders.COOKIE.toString(), "uid=buyeruid"));
    }

    @Test
    public void makeHttpRequestsShouldThrowPrebidExceptionIfMasterTagIdIsEqualsToZero() {
        // given
        final AdapterRequest adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                AdUnitBid.builder()
                        .params(Json.mapper.valueToTree(AdformParams.of(0L))).build()));
        final PreBidRequestContext preBidRequestContext = PreBidRequestContext.builder().build();

        // when and then
        assertThatThrownBy(() -> adformAdapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("master tag(placement) id is invalid=0");
    }

    @Test
    public void makeHttpRequestsShouldThrowPrebidExceptionIfAdUnitParamsIsNull() {
        // given
        final AdapterRequest adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                AdUnitBid.builder().build()));
        final PreBidRequestContext preBidRequestContext = PreBidRequestContext.builder().build();

        // when and then
        assertThatThrownBy(() -> adformAdapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Adform params section is missing");
    }

    @Test
    public void makeHttpRequestsShouldThrowPrebidExceptionIfAdUnitParamsCannotBeParsed() {
        // given
        final AdapterRequest adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                AdUnitBid.builder()
                        .params(Json.mapper.createObjectNode().put("mid", "string")).build()));
        final PreBidRequestContext preBidRequestContext = PreBidRequestContext.builder().build();

        // when and then
        assertThatThrownBy(() -> adformAdapter.makeHttpRequests(adapterRequest, preBidRequestContext))
                .isExactlyInstanceOf(PreBidException.class)
                .hasMessage("Cannot deserialize value of type `java.lang.Long` from String \"string\": not a valid Long"
                        + " value\n at [Source: UNKNOWN; line: -1, column: -1] (through reference chain: "
                        + "org.prebid.server.bidder.adform.model.AdformParams[\"mid\"])");
    }

    @Test
    public void makeHttpRequestShouldReturnAdapterHttpRequestWithHttpsProtocolIfSecured() {
        // given
        final AdapterRequest adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                AdUnitBid.builder()
                        .params(Json.mapper.valueToTree(AdformParams.of(15L))).build()));
        final PreBidRequestContext preBidRequestContext = PreBidRequestContext.builder().secure(1)
                .uidsCookie(uidsCookie)
                .preBidRequest(PreBidRequest.builder().build()).build();

        // when
        final List<AdapterHttpRequest<Void>> adapterHttpRequests = adformAdapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then

        // bWlkPTE1 is Base64 encoded "mid=15" value
        assertThat(adapterHttpRequests).hasSize(1)
                .extracting(AdapterHttpRequest::getUri)
                .containsExactly("https://adform.com/openrtb2d/?CC=1&rp=4&fd=1&stid=&ip=&bWlkPTE1");
    }

    @Test
    public void extractBidsShouldReturnBidBuilder() {
        // given
        final AdapterRequest adapterRequest = AdapterRequest.of(BIDDER, singletonList(AdUnitBid.builder()
                .bidId("bidId").adUnitCode("adUnitCode").build()));
        final ExchangeCall<Void, List<AdformBid>> exchangeCall = ExchangeCall.success(null,
                singletonList(AdformBid.builder().winBid(BigDecimal.ONE).banner("banner").response("banner")
                        .width(300).height(250).dealId("dealId").build()), null);

        // when
        final List<Bid> result = adformAdapter.extractBids(adapterRequest, exchangeCall).stream()
                .map(Bid.BidBuilder::build).collect(toList());

        // then
        assertThat(result).hasSize(1).containsExactly(Bid.builder().bidId("bidId").code("adUnitCode").bidder(BIDDER)
                .price(BigDecimal.ONE).adm("banner").width(300).height(250).dealId("dealId")
                .creativeId(MediaType.banner.name()).build());
    }

    @Test
    public void extractBidsShouldReturnEmptyListIfResponseTypeIsNotBanner() {
        // given
        final AdapterRequest adapterRequest = AdapterRequest.of(BIDDER, null);
        final ExchangeCall<Void, List<AdformBid>> exchangeCall = ExchangeCall.success(null,
                singletonList(AdformBid.builder().banner("banner").response("notBanner").build()), null);

        // when
        final List<Bid.BidBuilder> result = adformAdapter.extractBids(adapterRequest, exchangeCall);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void extractBidsShouldReturnEmptyListIfAdFormBidBannerIsNull() {
        // given
        final AdapterRequest adapterRequest = AdapterRequest.of(BIDDER, null);
        final ExchangeCall<Void, List<AdformBid>> exchangeCall = ExchangeCall.success(null,
                singletonList(AdformBid.builder().banner(null).response("banner").build()), null);

        // when
        final List<Bid.BidBuilder> result = adformAdapter.extractBids(adapterRequest, exchangeCall);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    public void extractBidsShouldReturnListWithOneBidBuilderWhenTwoBidsInResponseAndOneIsInvalid() {
        // given
        final AdapterRequest adapterRequest = AdapterRequest.of(BIDDER, singletonList(AdUnitBid.builder()
                .bidId("bidId").adUnitCode("adUnitCode").build()));
        final ExchangeCall<Void, List<AdformBid>> exchangeCall = ExchangeCall.success(null,
                asList(AdformBid.builder().winBid(BigDecimal.ONE).banner("banner").response("banner").build(),
                        AdformBid.builder().build()), null);

        // when
        final List<Bid> result = adformAdapter.extractBids(adapterRequest, exchangeCall).stream()
                .map(Bid.BidBuilder::build).collect(toList());

        // then
        assertThat(result).hasSize(1);
    }

    @Test
    public void tolerateErrorsShouldReturnFalse() {
        // when
        final boolean isTolerate = adformAdapter.tolerateErrors();

        // then
        assertThat(isTolerate).isFalse();
    }
}
