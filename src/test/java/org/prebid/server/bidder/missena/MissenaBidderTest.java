package org.prebid.server.bidder.missena;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.missena.ExtImpMissena;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singleton;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.prebid.server.util.HttpUtil.REFERER_HEADER;
import static org.prebid.server.util.HttpUtil.USER_AGENT_HEADER;
import static org.prebid.server.util.HttpUtil.X_FORWARDED_FOR_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

class MissenaBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test-url.com";

    private final MissenaBidder target = new MissenaBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));

        // when
        final Result<List<HttpRequest<MissenaAdRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("Error parsing missenaExt parameters"));
    }

    @Test
    public void makeHttpRequestsShouldMakeOneRequestPerImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("givenImp1").ext(givenImpExt("apiKey1", "plId1", "test1")),
                imp -> imp.id("givenImp2").ext(givenImpExt("apiKey2", "plId2", "test2")))
                .toBuilder()
                .id("requestId")
                .site(Site.builder().page("page").domain("domain").build())
                .regs(Regs.builder().ext(ExtRegs.of(1, null, null, null)).build())
                .user(User.builder().ext(ExtUser.builder().consent("consent").build()).build())
                .build();

        //when
        final Result<List<HttpRequest<MissenaAdRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        final MissenaAdRequest expectedRequest = MissenaAdRequest.builder()
                .requestId("requestId")
                .timeout(2000)
                .referer("page")
                .refererCanonical("domain")
                .consentString("consent")
                .consentRequired(true)
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getPayload)
                .containsExactlyInAnyOrder(
                        expectedRequest.toBuilder().placement("plId1").test("test1").build(),
                        expectedRequest.toBuilder().placement("plId2").test("test2").build());
    }

    @Test
    public void makeHttpRequestsShouldHaveImpIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("givenImp1"), imp -> imp.id("givenImp2"));
        //when
        final Result<List<HttpRequest<MissenaAdRequest>>> result = target.makeHttpRequests(bidRequest);

        //then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2)
                .extracting(HttpRequest::getImpIds)
                .containsExactlyInAnyOrder(singleton("givenImp1"), singleton("givenImp2"));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeadersWhenDeviceHasIp() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder()
                .site(Site.builder().page("page").build())
                .device(Device.builder().ua("ua").ip("ip").ipv6("ipv6").build())
                .build();

        // when
        final Result<List<HttpRequest<MissenaAdRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE))
                .satisfies(headers -> assertThat(headers.get(USER_AGENT_HEADER))
                        .isEqualTo("ua"))
                .satisfies(headers -> assertThat(headers.get(X_FORWARDED_FOR_HEADER))
                        .isEqualTo("ip"))
                .satisfies(headers -> assertThat(headers.get(REFERER_HEADER))
                        .isEqualTo("page"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeadersWhenDeviceHasIpv6Only() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity())
                .toBuilder()
                .device(Device.builder().ip(null).ipv6("ipv6").build())
                .build();

        // when
        final Result<List<HttpRequest<MissenaAdRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER)).isEqualTo(APPLICATION_JSON_VALUE))
                .satisfies(headers -> assertThat(headers.get(USER_AGENT_HEADER)).isNull())
                .satisfies(headers -> assertThat(headers.get(X_FORWARDED_FOR_HEADER)).isEqualTo("ipv6"))
                .satisfies(headers -> assertThat(headers.get(REFERER_HEADER)).isNull());
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void shouldMakeOneRequestWhenOneImpIsValidAndAnotherAreInvalid() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                imp -> imp.id("impId1").ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))),
                imp -> imp.id("impId2").ext(givenImpExt("apiKey", "placement", "testMode")));

        //when
        final Result<List<HttpRequest<MissenaAdRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final MissenaAdRequest expectedRequest = MissenaAdRequest.builder()
                .timeout(2000)
                .consentString("")
                .consentRequired(false)
                .test("testMode")
                .placement("placement")
                .build();

        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getPayload)
                .containsOnly(expectedRequest);
    }

    @Test
    public void makeHttpRequestsShouldUseCorrectUri() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.ext(givenImpExt("apiKey", "plId", "test")));

        // when
        final Result<List<HttpRequest<MissenaAdRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsExactly("https://test-url.com?t=apiKey");
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<MissenaAdRequest> httpCall = givenHttpCall("invalid");
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("impId1"), imp -> imp.id("impId2"))
                .toBuilder().id("requestId").build();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode");
        });
    }

    @Test
    public void makeBidsShouldReturnSingleBid() throws JsonProcessingException {
        // given
        final MissenaAdResponse bidResponse = MissenaAdResponse.builder()
                .requestId("id")
                .cpm(BigDecimal.TEN)
                .currency("USD")
                .ad("adm")
                .build();
        final BidderCall<MissenaAdRequest> httpCall = givenHttpCall(mapper.writeValueAsString(bidResponse));
        final BidRequest bidRequest = givenBidRequest(imp -> imp.id("impId1"), imp -> imp.id("impId2"))
                .toBuilder().id("requestId").build();

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();

        final Bid expetedBid = Bid.builder()
                .adm("adm")
                .price(BigDecimal.TEN)
                .crid("id")
                .impid("impId1")
                .id("requestId")
                .build();

        assertThat(result.getValue()).hasSize(1)
                .containsOnly(BidderBid.of(expetedBid, BidType.banner, "USD"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .imp(Arrays.stream(impCustomizers).map(MissenaBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .ext(givenImpExt("apikey", "placementId", "test")))
                .build();
    }

    private static ObjectNode givenImpExt(String apiKey, String placement, String testMode) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpMissena.of(apiKey, placement, testMode)));
    }

    private static BidderCall<MissenaAdRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<MissenaAdRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }

}
