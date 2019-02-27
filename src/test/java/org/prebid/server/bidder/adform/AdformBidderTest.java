package org.prebid.server.bidder.adform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import io.netty.handler.codec.http.HttpHeaderValues;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.proto.openrtb.ext.request.adform.ExtImpAdform;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

public class AdformBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://adform.com/openrtb2d";

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
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpAdform.of(15L, "gross")))).build()))
                .site(Site.builder().page("www.example.com").build())
                .regs(Regs.of(null, mapper.valueToTree(ExtRegs.of(1))))
                .user(User.builder().buyeruid("buyeruid").ext(mapper.valueToTree(ExtUser.of(
                        null, "consent", ExtUserDigiTrust.of("id", 123, 1), null))).build())
                .device(Device.builder().ua("ua").ip("ip").ifa("ifaId").build())
                .source(Source.builder().tid("tid").build())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adformBidder.makeHttpRequests(bidRequest);

        // then

        // bWlkPTE1 is Base64 encoded "mid=15" value
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly(
                        "http://adform.com/openrtb2d?CC=1&adid=ifaId&fd=1&gdpr=1&gdpr_consent=consent&ip=ip&pt=gross"
                                + "&rp=4&stid=tid&bWlkPTE1JnJjdXI9VVNE");
        assertThat(result.getValue()).extracting(HttpRequest::getMethod).containsExactly(HttpMethod.GET);

        assertThat(result.getValue()).
                flatExtracting(res -> res.getHeaders().entries())
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), HttpUtil.APPLICATION_JSON_CONTENT_TYPE),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), HttpHeaderValues.APPLICATION_JSON.toString()),
                        tuple(HttpUtil.USER_AGENT_HEADER.toString(), "ua"),
                        tuple(HttpUtil.X_FORWARDED_FOR_HEADER.toString(), "ip"),
                        tuple(HttpUtil.X_REQUEST_AGENT_HEADER.toString(), "PrebidAdapter 0.1.2"),
                        tuple(HttpUtil.REFERER_HEADER.toString(), "www.example.com"),
                        // Base64 encoded {"id":"id","version":1,"keyv":123,"privacy":{"optout":true}}
                        tuple(HttpUtil.COOKIE_HEADER.toString(),
                                "uid=buyeruid;DigiTrust.v1.identity=eyJpZCI6ImlkIiwidmVyc2lvbiI6MSwia2V5diI6MTIzLCJwcml"
                                        + "2YWN5Ijp7Im9wdG91dCI6dHJ1ZX19"));
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
                .containsExactly(BidderError.badInput(
                        "Adform adapter supports only banner Imps for now. Ignoring Imp ID=Imp12"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfMasterTagIsLessThanOne() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(singletonList(Imp.builder()
                        .id("Imp12")
                        .banner(Banner.builder().build())
                        .ext(Json.mapper.valueToTree(ExtPrebid.of(
                                null, ExtImpAdform.of(0L, null))))
                        .build()))
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adformBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .contains(BidderError.badInput("master tag(placement) id is invalid=0"));
    }

    @Test
    public void makeHttpRequestsShouldReturnHttpRequestsWithErrors() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .imp(asList(Imp.builder()
                                .banner(Banner.builder().build())
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpAdform.of(15L, null)))).build(),
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
                                .ext(Json.mapper.valueToTree(ExtPrebid.of(null, ExtImpAdform.of(15L, null)))).build(),
                        Imp.builder().build()))
                .source(Source.builder().tid("tid").build())
                .build();

        // when
        final Result<List<HttpRequest<Void>>> result = adformBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly("https://adform.com/openrtb2d?CC=1&fd=1&gdpr=&gdpr_consent=&ip=&rp=4&"
                        + "stid=tid&bWlkPTE1JnJjdXI9VVNE");
    }

    @Test
    public void makeBidsShouldReturnEmptyBidderWithErrorWhenResponseCantBeParsed() {
        // given
        final HttpCall<Void> httpCall = givenHttpCall("{");

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
        final HttpCall<Void> httpCall = givenHttpCall(adformResponse);
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
        final HttpCall<Void> httpCall = givenHttpCall(adformResponse);
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
                .response("banner").winCur("currency").dealId("dealId").height(300).width(400).winCrid("gross")
                .winBid(BigDecimal.ONE).build());

        final HttpCall<Void> httpCall = givenHttpCall(adformResponse);
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(Imp.builder().id("id").build())).build();

        // when
        final Result<List<BidderBid>> result = adformBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).containsOnly(BidderBid.of(
                Bid.builder().id("id").impid("id").price(BigDecimal.ONE).adm("admBanner").w(400).h(300).dealid("dealId")
                        .crid("gross").build(), BidType.banner, "currency"));
    }

    @Test
    public void makeBidsShouldSkipInvalidBidAndReturnValidBid() throws JsonProcessingException {
        // given
        final String adformResponse = mapper.writeValueAsString(asList(AdformBid.builder().banner("admBanner")
                .response("banner").build(), AdformBid.builder().build()));

        final HttpCall<Void> httpCall = givenHttpCall(adformResponse);
        final BidRequest bidRequest = BidRequest.builder().imp(asList(Imp.builder().id("id1").build(),
                Imp.builder().id("id2").build())).build();

        // when
        final Result<List<BidderBid>> result = adformBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).doesNotContainNull().element(0).isEqualTo(BidderBid.of(
                Bid.builder().id("id1").impid("id1").adm("admBanner").build(), BidType.banner, null));
    }

    private static HttpCall<Void> givenHttpCall(String body) {
        return HttpCall.success(null, HttpResponse.of(200, null, body), null);
    }
}
