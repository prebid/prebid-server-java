package org.prebid.server.bidder.teads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.teads.TeadsImpExt;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.UnaryOperator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.tuple;
import static org.prebid.server.bidder.model.BidderError.badInput;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.util.HttpUtil.ACCEPT_HEADER;
import static org.prebid.server.util.HttpUtil.APPLICATION_JSON_CONTENT_TYPE;
import static org.prebid.server.util.HttpUtil.CONTENT_TYPE_HEADER;
import static org.springframework.util.MimeTypeUtils.APPLICATION_JSON_VALUE;

public class TeadsBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "http://test-url.com";

    private final TeadsBidder target = new TeadsBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new TeadsBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenRequestHasInvalidImpression() {
        // given
        final ObjectNode invalidExt = mapper.valueToTree(ExtPrebid.of(null, mapper.createArrayNode()));
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder.ext(invalidExt));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).hasSize(1).first()
                .satisfies(error -> {
                    assertThat(error.getMessage()).startsWith("Cannot deserialize");
                    assertThat(error.getType()).isEqualTo(BidderError.Type.bad_input);
                });
    }

    @Test
    public void makeHttpRequestsShouldReturnErrorWhenImpExtHasInvalidPlacementId() {
        // given
        final BidRequest bidRequest = givenBidRequest(
                UnaryOperator.identity(),
                impBuilder -> impBuilder.ext(givenImpExt(0)));

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).containsExactly(badInput("placementId should not be 0"));
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedHeaders() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getValue()).hasSize(1).first()
                .extracting(HttpRequest::getHeaders)
                .satisfies(headers -> assertThat(headers.get(CONTENT_TYPE_HEADER))
                        .isEqualTo(APPLICATION_JSON_CONTENT_TYPE))
                .satisfies(headers -> assertThat(headers.get(ACCEPT_HEADER))
                        .isEqualTo(APPLICATION_JSON_VALUE));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedRequestUriAndMethod() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue())
                .extracting(HttpRequest::getMethod, HttpRequest::getUri)
                .containsExactly(tuple(HttpMethod.POST, ENDPOINT_URL));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnExpectedImpIds() {
        // given
        final BidRequest bidRequest = givenBidRequest(UnaryOperator.identity());

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        assertThat(results.getValue()).flatExtracting(HttpRequest::getImpIds).containsExactly("imp_id");
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnModifiedBannerWhenBannerHasFormats() {
        // given
        final Banner banner = Banner.builder().w(100).h(200)
                .format(List.of(
                        Format.builder().w(1).h(2).build(),
                        Format.builder().w(3).h(4).build()))
                .build();
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .banner(banner)
                .ext(givenImpExt(125)));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final Banner expectedBanner = banner.toBuilder().w(1).h(2).build();
        final BidRequest expectedRequest = givenBidRequest(impBuilder -> impBuilder
                .tagid("125")
                .banner(expectedBanner)
                .ext(expectedImpExt(125)));

        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest))
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnNotModifiedBannerWhenBannerDoesNotHaveFormats() {
        // given
        final Banner banner = Banner.builder().w(100).h(200).build();
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .banner(banner)
                .ext(givenImpExt(125)));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final BidRequest expectedRequest = givenBidRequest(impBuilder -> impBuilder
                .tagid("125")
                .banner(banner)
                .ext(expectedImpExt(125)));

        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest))
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldReturnVideoWhenWhenImpHasVideo() {
        // given
        final Video video = Video.builder().build();
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .video(video)
                .ext(givenImpExt(125)));

        // when
        final Result<List<HttpRequest<BidRequest>>> results = target.makeHttpRequests(bidRequest);

        // then
        final BidRequest expectedRequest = givenBidRequest(impBuilder -> impBuilder
                .tagid("125")
                .video(video)
                .ext(expectedImpExt(125)));

        assertThat(results.getValue()).hasSize(1).first()
                .satisfies(request -> assertThat(request.getPayload())
                        .isEqualTo(expectedRequest))
                .satisfies(request -> assertThat(request.getBody())
                        .isEqualTo(jacksonMapper.encodeToBytes(expectedRequest)));
        assertThat(results.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnErrorWhenResponseCanNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(null, "invalid");

        // when
        final Result<List<BidderBid>> actual = target.makeBids(httpCall, null);

        // then
        assertThat(actual.getValue()).isEmpty();
        assertThat(actual.getErrors()).containsExactly(BidderError.badServerResponse("Bad Server Response"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenSeatsAreEmpty() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = givenHttpCall(givenBidRequest(), givenBidResponse());

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(badInput("Empty SeatBid array"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenSeatHasNotExistingBid() throws JsonProcessingException {
        // given
        final Bid bannerBid = Bid.builder().impid("imp_id1").build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.id("imp_id2")),
                givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(badInput("Bid for the Imp imp_id1 wasn't found"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenRendererNameIsAbsent() throws JsonProcessingException {
        // given
        final ExtBidPrebidMeta meta = ExtBidPrebidMeta.builder().rendererName("").build();
        final ObjectNode bidExt = mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.builder().meta(meta).build(), null));
        final Bid bannerBid = Bid.builder().impid("imp_id").ext(bidExt).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.id("imp_id")),
                givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(badInput("RendererName should not be empty"));
    }

    @Test
    public void makeBidsShouldReturnErrorWhenRendererVersionIsAbsent() throws JsonProcessingException {
        // given
        final ExtBidPrebidMeta meta = ExtBidPrebidMeta.builder()
                .rendererName("rendererName")
                .rendererVersion("")
                .build();
        final ObjectNode bidExt = mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.builder().meta(meta).build(), null));
        final Bid bannerBid = Bid.builder().impid("imp_id").ext(bidExt).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.id("imp_id")),
                givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).containsExactly(badInput("RendererVersion should not be empty"));
    }

    @Test
    public void makeBidsShouldReturnBannerBid() throws JsonProcessingException {
        // given
        final ExtBidPrebidMeta meta = ExtBidPrebidMeta.builder()
                .rendererName("rendererName")
                .rendererVersion("rendererVersion")
                .rendererData(mapper.createObjectNode().set("property", TextNode.valueOf("value")))
                .rendererUrl("rendererUrl")
                .build();
        final ObjectNode bidExt = mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().meta(meta).build(), null));
        final Bid bannerBid = Bid.builder().impid("imp_id").ext(bidExt).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.id("imp_id").banner(Banner.builder().build())),
                givenBidResponse(bannerBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final ExtBidPrebidMeta expectedMeta = ExtBidPrebidMeta.builder()
                .rendererName("rendererName")
                .rendererVersion("rendererVersion")
                .build();
        final ObjectNode expectedBidExt = mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().meta(expectedMeta).build(), null));
        final Bid expectedBid = Bid.builder().impid("imp_id").ext(expectedBidExt).build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(expectedBid, banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBid() throws JsonProcessingException {
        // given
        final ExtBidPrebidMeta meta = ExtBidPrebidMeta.builder()
                .rendererName("rendererName")
                .rendererVersion("rendererVersion")
                .rendererData(mapper.createObjectNode().set("property", TextNode.valueOf("value")))
                .rendererUrl("rendererUrl")
                .build();
        final ObjectNode bidExt = mapper.valueToTree(ExtPrebid.of(ExtBidPrebid.builder().meta(meta).build(), null));
        final Bid videoBid = Bid.builder().impid("imp_id").ext(bidExt).build();

        final BidderCall<BidRequest> httpCall = givenHttpCall(
                givenBidRequest(impBuilder -> impBuilder.id("imp_id").video(Video.builder().build())),
                givenBidResponse(videoBid));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        final ExtBidPrebidMeta expectedMeta = ExtBidPrebidMeta.builder()
                .rendererName("rendererName")
                .rendererVersion("rendererVersion")
                .build();
        final ObjectNode expectedBidExt = mapper.valueToTree(
                ExtPrebid.of(ExtBidPrebid.builder().meta(expectedMeta).build(), null));
        final Bid expectedBid = Bid.builder().impid("imp_id").ext(expectedBidExt).build();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).containsOnly(BidderBid.of(expectedBid, video, "USD"));
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder>... impCustomizers) {
        return BidRequest.builder()
                .imp(Arrays.stream(impCustomizers).map(TeadsBidderTest::givenImp).toList())
                .build();
    }

    private static Imp givenImp(UnaryOperator<Imp.ImpBuilder> impCustomizer) {
        return impCustomizer.apply(Imp.builder().id("imp_id").ext(givenImpExt(125))).build();
    }

    private static ObjectNode expectedImpExt(int placementId) {
        return mapper.valueToTree(TeadsImpExtKV.of(TeadsImpExt.of(placementId)));
    }

    private static ObjectNode givenImpExt(int placementId) {
        return mapper.valueToTree(ExtPrebid.of(null, TeadsImpExt.of(placementId)));
    }

    private static String givenBidResponse(Bid... bids) throws JsonProcessingException {
        return mapper.writeValueAsString(
                BidResponse.builder()
                        .cur("USD")
                        .seatbid(bids.length == 0
                                ? Collections.emptyList()
                                : List.of(SeatBid.builder().bid(List.of(bids)).build()))
                        .build());
    }

    private static BidderCall<BidRequest> givenHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

}
