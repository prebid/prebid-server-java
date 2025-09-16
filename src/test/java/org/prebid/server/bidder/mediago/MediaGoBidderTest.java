package org.prebid.server.bidder.mediago;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.mediago.MediaGoImpExt;

import java.util.Arrays;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.badServerResponse;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.prebid.server.util.HttpUtil.X_OPENRTB_VERSION_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class MediaGoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.host.com/prebid/bid?region={{Host}}&token={{AccountID}}";

    private final MediaGoBidder target = new MediaGoBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MediaGoBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldFailWhenTokenCannotBeFoundInExtAndFirstImpExt() {
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(givenExtRequest("", "US")),
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, MediaGoImpExt.of("", "EU", "id")))),
                imp -> imp.ext(getImpExt("", "EU")),
                imp -> imp.ext(getImpExt("token", "EU")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).first()
                .isEqualTo(BidderError.badInput("mediago token not found"));
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldFailWhenTokenCannotBeFoundInExtAndFirstImpExtCannotBeParsed() {
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(givenExtRequest("", "US")),
                imp -> imp.ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()))),
                imp -> imp.ext(getImpExt("token", "EU")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                    assertThat(error.getMessage()).startsWith("Cannot deserialize value");
                });
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedRequestWithAllImps() {
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(givenExtRequest("token", "US")),
                imp -> imp.id("impId1"),
                imp -> imp.id("impId2"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(bidRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(bidRequest))
                .satisfies(request -> assertThat(request.getImpIds())
                        .containsExactlyInAnyOrder("impId1", "impId2"));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(givenExtRequest("token", "US")),
                imp -> imp.id("impId1"),
                imp -> imp.id("impId2"));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE))
                .satisfies(headers -> assertThat(headers.get(X_OPENRTB_VERSION_HEADER))
                        .isEqualTo("2.5"));
    }

    @Test
    public void makeHttpRequestsShouldReturnEndpointWithTokenAndRegionFromExtRequest() {
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(givenExtRequest("request_token", "APAC")),
                imp -> imp.id("impId1").ext(getImpExt("imp_token_1", "EU")),
                imp -> imp.id("impId2").ext(getImpExt("imp_token_2", "US")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getUri)
                .isEqualTo("https://test.host.com/prebid/bid?region=jp&token=request_token");
    }

    @Test
    public void makeHttpRequestsShouldReturnEndpointWithTokenAndDefaultRegionFromExtRequestWhenRegionIsNotPresent() {
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(givenExtRequest("request_token", null)),
                imp -> imp.id("impId1").ext(getImpExt("imp_token_1", "EU")),
                imp -> imp.id("impId2").ext(getImpExt("imp_token_2", "APAC")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getUri)
                .isEqualTo("https://test.host.com/prebid/bid?region=us&token=request_token");
    }

    @Test
    public void makeHttpRequestsShouldReturnEndpointWithTokenAndRegionFromFirstImpWhenTokenInExtRequestIsNotPresent() {
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(givenExtRequest("", null)),
                imp -> imp.id("impId1").ext(getImpExt("imp_token_1", "EU")),
                imp -> imp.id("impId2").ext(getImpExt("imp_token_2", "APAC")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getUri)
                .isEqualTo("https://test.host.com/prebid/bid?region=eu&token=imp_token_1");
    }

    @Test
    public void makeHttpRequestsShouldReturnEndpointWithTokenAndRegionFromFirstImpWhenTokenInExtReqCannotBeFound() {
        final ObjectNode bidderParams = mapper.createObjectNode()
                .set("randomBidder", mapper.createObjectNode().put("token", "request_token"));

        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(ExtRequest.of(ExtRequestPrebid.builder().bidderparams(bidderParams).build())),
                imp -> imp.id("impId1").ext(getImpExt("imp_token_1", "US")),
                imp -> imp.id("impId2").ext(getImpExt("imp_token_2", "EU")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getUri)
                .isEqualTo("https://test.host.com/prebid/bid?region=us&token=imp_token_1");
    }

    @Test
    public void makeHttpRequestsShouldReturnEndpointWithTokenAndDefaultRegionFromFirstImpWhenTokenInExtReqIsNotFound() {
        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(givenExtRequest(null, null)),
                imp -> imp.id("impId1").ext(getImpExt("imp_token_1", "unknownRefion")),
                imp -> imp.id("impId2").ext(getImpExt("imp_token_2", "APAC")));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getUri)
                .isEqualTo("https://test.host.com/prebid/bid?region=us&token=imp_token_1");
    }

    @Test
    public void makeHttpRequestsShouldModifyBannerDimensionWithFirstFormatWhenBannerHasNotSetDimensions() {
        final Banner bannerToModify = Banner.builder().w(0).h(null)
                .format(List.of(
                        Format.builder().h(1).w(1).build(),
                        Format.builder().h(2).w(2).build()))
                .build();

        final Banner bannerToKeep = Banner.builder().w(3).h(3)
                .format(List.of(
                        Format.builder().h(1).w(1).build(),
                        Format.builder().h(2).w(2).build()))
                .build();

        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(givenExtRequest("token", "US")),
                imp -> imp.id("impId1").banner(bannerToModify),
                imp -> imp.id("impId2").banner(bannerToKeep),
                imp -> imp.id("impId3").xNative(Native.builder().build()));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        final Banner expectedModifiedBanner = Banner.builder().w(1).h(1)
                .format(List.of(
                        Format.builder().h(1).w(1).build(),
                        Format.builder().h(2).w(2).build()))
                .build();

        final BidRequest expectedBidRequest = givenBidRequest(
                request -> request.ext(givenExtRequest("token", "US")),
                imp -> imp.id("impId1").banner(expectedModifiedBanner),
                imp -> imp.id("impId2").banner(bannerToKeep),
                imp -> imp.id("impId3").xNative(Native.builder().build()));

        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedBidRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedBidRequest))
                .satisfies(request -> assertThat(request.getImpIds())
                        .containsExactlyInAnyOrder("impId1", "impId2", "impId3"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldKeepBannerAsIsWhenDimensionsNotSetButFormatsAreAbsent() {
        final Banner bannerToKeep = Banner.builder().w(null).h(3).build();

        final BidRequest bidRequest = givenBidRequest(
                request -> request.ext(givenExtRequest("token", "US")),
                imp -> imp.id("impId1").banner(bannerToKeep));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(bidRequest)))
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(bidRequest))
                .satisfies(request -> assertThat(request.getImpIds())
                        .containsExactlyInAnyOrder("impId1"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseCanNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).allSatisfy(error -> {
            assertThat(error.getType()).isEqualTo(BidderError.Type.bad_server_response);
            assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
        });
    }

    @Test
    public void makeBidsShouldReturnEmptyResponseWhenResponseDoesNotHaveSeatBid() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, givenBidResponse());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().impid("1").mtype(1).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bannerBid, banner, "USD"));

    }

    @Test
    public void makeBidsShouldReturnNativeBidSuccessfully() throws JsonProcessingException {
        // given
        final Bid nativeBid = Bid.builder().impid("3").mtype(4).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(identity()), givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(nativeBid, xNative, "USD"));
    }

    @Test
    public void shouldReturnErrorWhileMakingBidsWhenImpTypeIsNotSupported() throws JsonProcessingException {
        // given
        final Bid audioBid = Bid.builder().impid("3").mtype(3).build();
        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                givenBidResponse(audioBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors())
                .containsExactly(badServerResponse("Unsupported MType 3"));
    }

    @Test
    public void makeBidsShouldReturnBannerBidWhenMtypeIsAbsentButBannerIsFoundInPayload()
            throws JsonProcessingException {

        // given
        final Bid bannerBid = Bid.builder().impid("1").mtype(null).build();

        final BidRequest payload = givenBidRequest(imp -> imp.id("1").banner(Banner.builder().build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(payload, givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(bannerBid, banner, "USD"));

    }

    @Test
    public void makeBidsShouldReturnNativeBidWhenMtypeIsAbsentButNativeIsFoundInPayload()
            throws JsonProcessingException {

        // given
        final Bid nativeBid = Bid.builder().impid("3").mtype(null).build();

        final BidRequest payload = givenBidRequest(imp -> imp.id("3").xNative(Native.builder().build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(payload, givenBidResponse(nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsExactly(BidderBid.of(nativeBid, xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenMtypeIsAbsentAndVideoFoundInPayloadButNotSupported()
            throws JsonProcessingException {

        // given
        final Bid videoBid = Bid.builder().impid("2").mtype(7).build();
        final BidRequest payload = givenBidRequest(imp -> imp.id("3").video(Video.builder().build()));
        final BidderCall<BidRequest> httpCall = givenHttpCall(payload, givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(badServerResponse("Unsupported MType 7"));
    }

    @Test
    public void makeBidsShouldReturnAllValidBidsRegardlessOfErrors() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().impid("1").mtype(1).build();
        final Bid videoBid = Bid.builder().impid("2").mtype(2).build();
        final Bid audioBid = Bid.builder().impid("3").mtype(3).build();
        final Bid nativeBid = Bid.builder().impid("4").mtype(4).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(identity()),
                givenBidResponse(bannerBid, videoBid, audioBid, nativeBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(
                badServerResponse("Unsupported MType 2"),
                badServerResponse("Unsupported MType 3"));

        assertThat(result.getValue()).containsExactly(
                BidderBid.of(bannerBid, banner, "USD"),
                BidderBid.of(nativeBid, xNative, "USD"));
    }

    private static BidRequest givenBidRequest(
            UnaryOperator<BidRequest.BidRequestBuilder> bidRequestCustomizer,
            UnaryOperator<Imp.ImpBuilder>... impCustomizers) {

        return bidRequestCustomizer.apply(BidRequest.builder()
                        .imp(Arrays.stream(impCustomizers).map(MediaGoBidderTest::givenImp).toList()))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return givenBidRequest(identity(), impCustomizer);
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder()
                        .id("impId")
                        .ext(getImpExt("imp_token", "EU")))
                .build();
    }

    private static ObjectNode getImpExt(String token, String region) {
        return mapper.valueToTree(ExtPrebid.of(null, MediaGoImpExt.of(token, region, "placementId")));
    }

    private static ExtRequest givenExtRequest(String token, String region) {
        final ObjectNode bidderParams = mapper.createObjectNode()
                .set("mediago", mapper.createObjectNode()
                        .put("token", token)
                        .put("region", region));
        return ExtRequest.of(ExtRequestPrebid.builder().bidderparams(bidderParams).build());
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(List.of(bids)).build()))
                .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest payload, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(payload).build(),
                HttpResponse.of(200, null, body),
                null);
    }

}
