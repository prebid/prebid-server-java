package org.prebid.server.bidder.rhythmone;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.rhythmone.ExtImpRhythmone;
import org.prebid.server.util.HttpUtil;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class RhythmoneBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test.domain.com/rmp";

    private RhythmoneBidder rhythmoneBidder;

    @Before
    public void setUp() {
        rhythmoneBidder = new RhythmoneBidder(ENDPOINT_URL);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new RhythmoneBidder("invalid_ulr"));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = rhythmoneBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("ext data not provided in imp id=123. Abort all Request"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorIfImpExtBidderCouldNotBeParsed() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                impBuilder -> impBuilder
                        .id("123")
                        .ext(mapper.valueToTree(ExtPrebid.of(null,
                                mapper.valueToTree(ExtImpRhythmone.builder().build())))));
        // when
        final Result<List<HttpRequest<BidRequest>>> result = rhythmoneBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(BidderError.badInput("placementId | zone | path not provided in imp id=123. Abort all " +
                        "Request"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldSetImpExtFieldS2STrue() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rhythmoneBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1)
                .extracting(bidRequestHttpRequest ->
                        mapper.readValue(bidRequestHttpRequest.getBody(), BidRequest.class))
                .flatExtracting(BidRequest::getImp)
                .extracting(Imp::getExt)
                .extracting(node -> node.get("bidder"))
                .extracting(bidder -> mapper.convertValue(bidder, ExtImpRhythmone.class))
                .extracting(ExtImpRhythmone::getS2s)
                .containsOnly(true);
    }

    @Test
    public void makeHttpRequestsShouldFillMethodAndUrlAndExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest(identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> result = rhythmoneBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).doesNotContainNull()
                .hasSize(1).element(0)
                .returns(HttpMethod.POST, HttpRequest::getMethod)
                .returns("http://test.domain.com/rmp/placementId/0/somePath?z=zone1&s2s=true", HttpRequest::getUri);
        assertThat(result.getValue().get(0).getHeaders()).isNotNull()
                .extracting(Map.Entry::getKey, Map.Entry::getValue)
                .containsOnly(
                        tuple(HttpUtil.CONTENT_TYPE_HEADER.toString(), "application/json;charset=utf-8"),
                        tuple(HttpUtil.ACCEPT_HEADER.toString(), "application/json"));
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null, "invalid body");

        // when
        final Result<List<BidderBid>> result = rhythmoneBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getErrors().get(0).getMessage()).startsWith("Failed to decode: Unrecognized token");
        assertThat(result.getErrors().get(0).getType()).isEqualTo(BidderError.Type.bad_server_response);
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = rhythmoneBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(null,
                mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = rhythmoneBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfImpIdNotEqualToBidImpId() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.id("123")
                        .banner(Banner.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("321"))));

        // when
        final Result<List<BidderBid>> result = rhythmoneBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("321").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfRequestImpHasBannerAndVideo() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(builder -> builder.id("123")
                        .video(Video.builder().build())
                        .banner(Banner.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = rhythmoneBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfBidRequestHasVideoAndNoBanner() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(builder -> builder.id("123")
                        .video(Video.builder().build())),
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = rhythmoneBidder.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsOnly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void extractTargetingShouldReturnEmptyMap() {
        assertThat(rhythmoneBidder.extractTargeting(mapper.createObjectNode())).isEqualTo(emptyMap());
    }

    private static BidRequest givenBidRequest(
            Function<BidRequest.BidRequestBuilder, BidRequest.BidRequestBuilder> bidRequestCustomizer,
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpRhythmone.ExtImpRhythmoneBuilder, ExtImpRhythmone.ExtImpRhythmoneBuilder> extCustomizer) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                .imp(singletonList(givenImp(impCustomizer, extCustomizer))))
                .build();
    }

    private static BidRequest givenBidRequest(Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer, identity());
    }

    private static Imp givenImp(
            Function<Imp.ImpBuilder, Imp.ImpBuilder> impCustomizer,
            Function<ExtImpRhythmone.ExtImpRhythmoneBuilder, ExtImpRhythmone.ExtImpRhythmoneBuilder> extCustomizer) {

        return impCustomizer.apply(Imp.builder()
                .id("123")
                .ext(mapper.valueToTree(ExtPrebid.of(null,
                        extCustomizer.apply(ExtImpRhythmone.builder())
                                .placementId("placementId")
                                .path("somePath")
                                .zone("zone1")
                                .build()))))
                .build();
    }

    private static BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }
}
