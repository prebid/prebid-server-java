package org.prebid.server.bidder.adform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.adform.model.AdformBid;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.adform.ExtImpAdform;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class AdformBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://adform.com/openrtb2d";
    private static final CharSequence X_REQUEST_AGENT = HttpHeaders.createOptimized("X-Request-Agent");
    private static final CharSequence X_FORWARDED_FOR = HttpHeaders.createOptimized("X-Forwarded-For");
    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";
    private AdformBidder adformBidder;

    @Before
    public void setUp() {
        adformBidder = new AdformBidder(ENDPOINT_URL);
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestWithoutErrors() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .banner(Banner.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpAdform.of(15L)))).build()))
                .site(Site.builder().page("www.example.com").build())
                .user(User.builder().buyeruid("buyeruid").build())
                .device(Device.builder().ua("ua").ip("ip").ifa("ifaId").build())
                .source(Source.builder().tid("tid").build())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adformBidder.makeHttpRequests(bidRequest);

        // then

        // bWlkPTE1 is Base64 encoded "mid=15" value
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("http://adform.com/openrtb2d/?CC=1&rp=4&fd=1&stid=tid&ip=ip&adid=ifaId&bWlkPTE1");
        assertThat(result.getValue()).extracting(HttpRequest::getMethod).containsExactly(HttpMethod.GET);

        assertThat(result.getValue()).
                flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple(HttpHeaders.CONTENT_TYPE.toString(), APPLICATION_JSON),
                        tuple(HttpHeaders.ACCEPT.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpHeaders.USER_AGENT.toString(), "ua"),
                        tuple(X_FORWARDED_FOR.toString(), "ip"),
                        tuple(X_REQUEST_AGENT.toString(), "PrebidAdapter 0.1.1"),
                        tuple(HttpHeaders.REFERER.toString(), "www.example.com"),
                        tuple(HttpHeaders.COOKIE.toString(), "uid=buyeruid"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfMediaTypeIsNotBanner() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("Imp12")
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adformBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .containsExactly("Adform adapter supports only banner Imps for now. Ignoring Imp ID=Imp12");
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfMasterTagIsLessThanOne() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("Imp12")
                        .banner(Banner.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(
                                null, ExtImpAdform.of(0L))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adformBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .extracting(BidderError::getMessage)
                .contains("master tag(placement) id is invalid=0");
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestsWithErrors() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpAdform.of(15L)))).build(),
                        Imp.builder().build()))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adformBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getErrors()).hasSize(1);
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpsUrlIfAtLeastOneImpIsSecured() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .banner(Banner.builder().build())
                                .secure(1)
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpAdform.of(15L)))).build(),
                        Imp.builder().build()))
                .source(Source.builder().tid("tid").build())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adformBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://adform.com/openrtb2d/?CC=1&rp=4&fd=1&stid=tid&ip=&bWlkPTE1");
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderBidsAndEmptyErrorsIfResponseStatusIsNoContent() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall(HttpResponseStatus.NO_CONTENT.code(), null);

        // when
        final Result<List<BidderBid>> result = adformBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderBidsAndErrorMessageIfResponseStatusIsNotOk() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall(HttpResponseStatus.INTERNAL_SERVER_ERROR.code(), null);

        // when
        final Result<List<BidderBid>> result = adformBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1).extracting(BidderError::getMessage).containsExactly(
                "unexpected status code: 500. Run with request.debug = 1 for more info");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderWithErrorWhenResponseCantBeParsed() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall(HttpResponseStatus.OK.code(), "{");

        // when
        final Result<List<BidderBid>> result = adformBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors().get(0).getMessage()).startsWith(
                "Unexpected end-of-input: expected close marker for Object");
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfResponseBannerFieldIsEmpty() throws JsonProcessingException {
        // given
        final String adformResponse = mapper.writeValueAsString(AdformBid.builder().build());
        final HttpCall<Void> httpCall = givenHttpCall(HttpResponseStatus.OK.code(), adformResponse);
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().build())).build();

        // when
        final Result<List<BidderBid>> result = adformBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfResponseTypeIsNotBanner() throws JsonProcessingException {
        // given
        final String adformResponse = mapper.writeValueAsString(AdformBid.builder().banner("someBanner")
                .response("notBanner").build());
        final HttpCall<Void> httpCall = givenHttpCall(HttpResponseStatus.OK.code(), adformResponse);
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().build())).build();

        // when
        final Result<List<BidderBid>> result = adformBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBidderBid() throws JsonProcessingException {
        // given
        final String adformResponse = mapper.writeValueAsString(AdformBid.builder().banner("admBanner")
                .response("banner").winCur("currency").dealId("dealId").height(300).width(400)
                .winBid(BigDecimal.ONE).build());

        final HttpCall<Void> httpCall = givenHttpCall(HttpResponseStatus.OK.code(), adformResponse);
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("id").build())).build();

        // when
        final Result<List<BidderBid>> result = adformBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).containsOnly(BidderBid.of(
                Bid.builder().id("id").impid("id").price(BigDecimal.ONE).adm("admBanner").w(400).h(300).dealid("dealId")
                        .build(), BidType.banner, null));
    }

    @Test
    public void makeBidsShouldSkipInvalidBidAndReturnValidBid() throws JsonProcessingException {
        // given
        final String adformResponse = mapper.writeValueAsString(asList(AdformBid.builder().banner("admBanner")
                .response("banner").build(), AdformBid.builder().build()));

        final HttpCall<Void> httpCall = givenHttpCall(HttpResponseStatus.OK.code(), adformResponse);
        final BidRequest bidRequest = BidRequest.builder().imp(asList(Imp.builder().id("id1").build(),
                Imp.builder().id("id2").build())).build();

        // when
        final Result<List<BidderBid>> result = adformBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull().element(0).isEqualTo(BidderBid.of(
                Bid.builder().id("id1").impid("id1").adm("admBanner").build(), BidType.banner, null));
    }

    private static HttpCall<Void> givenHttpCall(int statusCode, String body) {
        return HttpCall.full(null, HttpResponse.of(statusCode, null, body), null);
    }
}
