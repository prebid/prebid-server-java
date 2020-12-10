package org.prebid.server.bidder.adform;

import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.http.HttpMethod;
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
import org.prebid.server.bidder.adform.model.AdformBid;
import org.prebid.server.bidder.adform.model.AdformParams;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.Base64;
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

    private static final String BIDDER = "adform";
    private static final String COOKIE_FAMILY = BIDDER;
    private static final String ENDPOINT_URL = "http://adform.com/openrtb2d";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private UidsCookie uidsCookie;

    private AdformAdapter adformAdapter;

    @Before
    public void setUp() {
        adformAdapter = new AdformAdapter(COOKIE_FAMILY, ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void makeHttpRequestsShouldReturnAdapterHttpRequestWithCorrectUrlAndHeaders() {
        // given
        final AdapterRequest adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                AdUnitBid.builder().bidId("bidId").adUnitCode("AdUnitCode")
                        .params(mapper.valueToTree(
                                AdformParams.of(15L, "gross", "color:red", "red", "300X60", 2.5,
                                        "https://adform.com?a=b")))
                        .build()));
        final PreBidRequestContext preBidRequestContext = PreBidRequestContext.builder().preBidRequest(
                PreBidRequest.builder().tid("tid").device(Device.builder().ifa("ifaId").build())
                        .regs(Regs.of(null, ExtRegs.of(1, null)))
                        .user(User.builder()
                                .ext(ExtUser.builder()
                                        .consent("consent")
                                        .build())
                                .build())
                        .build())
                .secure(0).ua("userAgent").ip("192.168.0.1").referer("www.example.com").uidsCookie(uidsCookie).build();

        given(uidsCookie.uidFrom(BIDDER)).willReturn("buyeruid");

        // when
        final List<AdapterHttpRequest<Void>> adapterHttpRequests = adformAdapter.makeHttpRequests(adapterRequest,
                preBidRequestContext);

        // then
        final String expectedEncodedPart = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("mid=15&rcur=USD&mkv=color:red&mkw=red&cdims=300X60&minp=2.50".getBytes());
        assertThat(adapterHttpRequests).hasSize(1)
                .extracting(AdapterHttpRequest::getUri)
                .containsExactly(
                        "http://adform.com/openrtb2d?CC=1&adid=ifaId&fd=1&gdpr=1&gdpr_consent=consent&ip=192.168.0.1"
                                + "&pt=gross&rp=4&stid=tid&url=https%3A%2F%2Fadform.com%3Fa%3Db"
                                + "&" + expectedEncodedPart);
        assertThat(adapterHttpRequests)
                .extracting(AdapterHttpRequest::getMethod)
                .containsExactly(HttpMethod.GET);

        assertThat(adapterHttpRequests)
                .extracting(AdapterHttpRequest::getPayload).containsNull();

        assertThat(adapterHttpRequests)
                .flatExtracting(adapterHttpRequest -> adapterHttpRequest.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "userAgent"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "192.168.0.1"),
                        tuple(HttpUtil.X_REQUEST_AGENT_HEADER.toString(), "PrebidAdapter 0.1.3"),
                        tuple(HttpUtil.REFERER_HEADER.toString(), "www.example.com"),
                        // Base64 encoded {"id":"id","version":1,"keyv":123,"privacy":{"optout":true}}
                        tuple(HttpUtil.COOKIE_HEADER.toString(), "uid=buyeruid"));
    }

    @Test
    public void makeHttpRequestsShouldThrowPrebidExceptionIfMasterTagIdIsEqualsToZero() {
        // given
        final AdapterRequest adapterRequest = AdapterRequest.of(BIDDER, singletonList(
                AdUnitBid.builder()
                        .params(mapper.valueToTree(AdformParams.of(0L, null, null, null, null, null, null))).build()));
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
                        .params(mapper.createObjectNode().put("mid", "string")).build()));
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
                        .params(mapper.valueToTree(AdformParams.of(15L, null, null, null, null, null, null))).build()));
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
                .containsExactly("https://adform.com/openrtb2d?CC=1&fd=1&gdpr=&gdpr_consent=&ip=&rp=4&"
                        + "stid=&bWlkPTE1JnJjdXI9VVNE");
    }

    @Test
    public void extractBidsShouldReturnBidBuilder() {
        // given
        final AdapterRequest adapterRequest = AdapterRequest.of(BIDDER, singletonList(AdUnitBid.builder()
                .bidId("bidId").adUnitCode("adUnitCode").build()));
        final ExchangeCall<Void, List<AdformBid>> exchangeCall = ExchangeCall.success(null,
                singletonList(AdformBid.builder().winBid(BigDecimal.ONE).banner("banner").response("banner")
                        .width(300).height(250).dealId("dealId").winCrid("gross").build()), null);

        // when
        final List<Bid> result = adformAdapter.extractBids(adapterRequest, exchangeCall).stream()
                .map(Bid.BidBuilder::build).collect(toList());

        // then
        assertThat(result).hasSize(1).containsExactly(Bid.builder().bidId("bidId").code("adUnitCode").bidder(BIDDER)
                .price(BigDecimal.ONE).adm("banner").width(300).height(250).dealId("dealId")
                .creativeId("gross").mediaType(MediaType.banner).build());
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
