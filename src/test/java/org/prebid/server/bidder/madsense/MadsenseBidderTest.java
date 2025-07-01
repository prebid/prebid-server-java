package org.prebid.server.bidder.madsense;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.madsense.ExtImpMadsense;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.Type;
import static org.prebid.server.bidder.model.BidderError.badInput;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.prebid.server.util.HttpUtil.ORIGIN_HEADER;
import static org.prebid.server.util.HttpUtil.REFERER_HEADER;
import static org.prebid.server.util.HttpUtil.USER_AGENT_HEADER;
import static org.prebid.server.util.HttpUtil.X_FORWARDED_FOR_HEADER;
import static org.prebid.server.util.HttpUtil.X_OPENRTB_VERSION_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

@ExtendWith(MockitoExtension.class)
public class MadsenseBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://ads.madsense.io/pbs";

    private MadsenseBidder target;

    @BeforeEach
    public void setUp() {
        target = new MadsenseBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MadsenseBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenFirstImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp
                .banner(Banner.builder().build())
                .ext(givenInvalidImpExt()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).containsExactly(badInput("Error parsing imp.ext parameters"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldCreateSeparateRequestForEachBannerImp() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.id("bannerImp1").banner(Banner.builder().build())
                        .ext(givenImpExt("testCompanyId")),
                builder -> builder.id("bannerImp2").banner(Banner.builder().build())
                        .ext(givenImpExt("otherCompanyId")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(2);
        assertThat(result.getValue())
                .extracting(HttpRequest::getImpIds)
                .containsExactlyInAnyOrder(Set.of("bannerImp1"), Set.of("bannerImp2"));

        assertThat(result.getValue())
                .extracting(HttpRequest::getUri)
                .containsOnly(ENDPOINT_URL + "?company_id=testCompanyId");
    }

    @Test
    public void makeHttpRequestsShouldCreateOneRequestForAllVideoImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.id("videoImp1").video(Video.builder().build())
                        .ext(givenImpExt("testCompanyId")),
                builder -> builder.id("videoImp2").video(Video.builder().build())
                        .ext(givenImpExt("otherCompanyId")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        assertThat(result.getValue().getFirst().getImpIds()).containsExactlyInAnyOrder("videoImp1", "videoImp2");
    }

    @Test
    public void makeHttpRequestsShouldHandleMixedBannerAndVideoImps() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.id("bannerImp1").banner(Banner.builder().build())
                        .ext(givenImpExt("testCompanyId")),
                builder -> builder.id("videoImp1").video(Video.builder().build())
                        .ext(givenImpExt("otherCompanyId1")),
                builder -> builder.id("bannerImp2").banner(Banner.builder().build())
                        .ext(givenImpExt("otherCompanyId2")),
                builder -> builder.id("videoImp2").video(Video.builder().build())
                        .ext(givenImpExt("otherCompanyId3")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(3)
                .extracting(HttpRequest::getImpIds)
                .containsExactly(Set.of("bannerImp1"), Set.of("bannerImp2"), Set.of("videoImp1", "videoImp2"));
    }

    @Test
    public void makeHttpRequestsShouldUseTestCompanyIdWhenRequestTestIsOne() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                builder -> builder.banner(Banner.builder().build()).ext(givenImpExt("testCompanyId")))
                .toBuilder()
                .test(1)
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(HttpRequest::getUri)
                .containsExactly(ENDPOINT_URL + "?company_id=test");
    }

    @Test
    public void makeHttpRequestsShouldSetCorrectHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(Banner.builder().build()))
                .toBuilder()
                .device(Device.builder().ua("ua").ip("ip").ipv6("ipv6").build())
                .site(Site.builder().domain("domain").ref("referrer").build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> {
                    assertThat(headers.get(CONTENT_TYPE_HEADER)).isEqualTo(APPLICATION_JSON_CONTENT_TYPE);
                    assertThat(headers.get(ACCEPT_HEADER)).isEqualTo(APPLICATION_JSON_VALUE);
                    assertThat(headers.get(X_OPENRTB_VERSION_HEADER)).isEqualTo("2.6");
                    assertThat(headers.get(USER_AGENT_HEADER)).isEqualTo("ua");
                    assertThat(headers.get(X_FORWARDED_FOR_HEADER)).isEqualTo("ip");
                    assertThat(headers.get(ORIGIN_HEADER)).isEqualTo("domain");
                    assertThat(headers.get(REFERER_HEADER)).isEqualTo("referrer");
                });
    }

    @Test
    public void makeHttpRequestsShouldSetIpv6WhenIpIsMissing() {
        // given
        final BidRequest bidRequest = givenBidRequest(imp -> imp.banner(Banner.builder().build()))
                .toBuilder()
                .device(Device.builder().ipv6("ipv6").ip(null).build())
                .build();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue().getFirst().getHeaders().get(X_FORWARDED_FOR_HEADER))
                .isEqualTo("ipv6");
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall("invalid_json");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(Type.bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token 'invalid_json'");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListWhenSeatBidIsEmpty() throws JsonProcessingException {
        // given
        final BidResponse bidResponseEmptySeatBid = BidResponse.builder().seatbid(emptyList()).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(bidResponseEmptySeatBid));

        // when
        final Result<List<BidderBid>> resultEmptySeatBid = target.makeBids(httpCall, null);

        // then
        assertThat(resultEmptySeatBid.getErrors()).isEmpty();
        assertThat(resultEmptySeatBid.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().id("bidId1").impid("impId1").price(BigDecimal.ONE)
                .adm("adm1").mtype(1).cat(singletonList("cat1")).build();
        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bannerBid)).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .containsExactly(BidderBid.of(bannerBid, BidType.banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidSuccessfullyWithVideoInfo() throws JsonProcessingException {
        // given
        final Bid videoBid = Bid.builder().id("bidId1").mtype(2).cat(singletonList("cat-video")).dur(30).build();
        final BidResponse bidResponse = BidResponse.builder()
                .cur("EUR")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(videoBid)).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final BidderBid expectedBidderBid = BidderBid.builder()
                .bid(videoBid)
                .type(BidType.video)
                .bidCurrency("EUR")
                .videoInfo(ExtBidPrebidVideo.of(30, "cat-video"))
                .build();

        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).containsExactly(expectedBidderBid);
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidMtypeIsMissing() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().id("bidId1").impid("impId1").build();
        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bid)).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(badServerResponse("Unsupported bid mediaType: null for impression: impId1"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenBidMtypeIsUnsupported() throws JsonProcessingException {
        // given
        final Bid bid = Bid.builder().id("bidId1").impid("impId1").mtype(3).build();
        final BidResponse bidResponse = BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bid)).build()))
                .build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(mapper.writeValueAsString(bidResponse));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .containsExactly(badServerResponse("Unsupported bid mediaType: 3 for impression: impId1"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        final List<Imp> imps = Arrays.stream(impCustomizers)
                .map(MadsenseBidderTest::givenImp)
                .toList();
        return BidRequest.builder().imp(imps).cur(singletonList("USD")).build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("test-imp-id")
                        .ext(givenImpExt("testCompanyId")))
                .build();
    }

    private static ObjectNode givenImpExt(String companyId) {
        return mapper.valueToTree(ExtPrebid.of(null, ExtImpMadsense.of(companyId)));
    }

    private static ObjectNode givenInvalidImpExt() {
        return mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
    }

    private static BidderCall<BidRequest> givenHttpCall(String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
