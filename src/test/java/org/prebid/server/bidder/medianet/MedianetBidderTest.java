package org.prebid.server.bidder.medianet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Native;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;

import java.util.List;
import java.util.function.Function;
import java.util.function.UnaryOperator;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.bidder.model.BidderError.Type.bad_server_response;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;

public class MedianetBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.media.net?src=external.prebidserver.com";

    private MedianetBidder medianetBidder;

    @Before
    public void setup() {
        medianetBidder = new MedianetBidder(ENDPOINT_URL, jacksonMapper);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new MedianetBidder("invalid_url", jacksonMapper));
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .id("123")
                .banner(Banner.builder().build()));
        final HttpCall<BidRequest> httpCall = sampleHttpCall(
                bidRequest,
                mapper.writeValueAsString(
                        givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = medianetBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnVideoBidIfVideoIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .id("123")
                .video(Video.builder().build()));
        final HttpCall<BidRequest> httpCall = sampleHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = medianetBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), video, "USD"));
    }

    @Test
    public void makeBidsShouldReturnxNativeBidIfxNativeIsPresentInRequestImp() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .id("123")
                .xNative(Native.builder().build()));
        final HttpCall<BidRequest> httpCall = sampleHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = medianetBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), xNative, "USD"));
    }

    @Test
    public void makeBidsShouldReturnErrorPreBidExceptionIfNotFoundAnyImpressions() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .id("123")
                .audio(Audio.builder().build()));
        final HttpCall<BidRequest> httpCall = sampleHttpCall(
                bidRequest,
                mapper.writeValueAsString(givenBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = medianetBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to find impression");
                });
    }

    @Test
    public void makeBitsShouldReturnEmptyValueIfSeatBitNotPresent() throws JsonProcessingException {
        // given
        final BidRequest bidRequest = givenBidRequest(impBuilder -> impBuilder
                .id("123")
                .video(Video.builder().build()));
        final HttpCall<BidRequest> httpCall = sampleHttpCall(
                bidRequest,
                mapper.writeValueAsString(BidResponse.builder()
                        .cur("USD")
                        .seatbid(singletonList(null))
                        .build()));

        // when
        final Result<List<BidderBid>> result = medianetBidder.makeBids(httpCall, bidRequest);

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> result = medianetBidder.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsExactly(bidRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final HttpCall<BidRequest> httpCall = sampleHttpCall(
                givenBidRequest(), "invalid response");

        // when
        final Result<List<BidderBid>> result = medianetBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).hasSize(1)
                .allSatisfy(error -> {
                    assertThat(error.getType()).isEqualTo(bad_server_response);
                    assertThat(error.getMessage()).startsWith("Failed to decode: Unrecognized token");
                });
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = sampleHttpCall(
                givenBidRequest(), mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = medianetBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final HttpCall<BidRequest> httpCall = sampleHttpCall(
                givenBidRequest(),
                mapper.writeValueAsString(BidRequest.builder().build()));
        // when
        final Result<List<BidderBid>> result = medianetBidder.makeBids(httpCall, givenBidRequest());

        // then
        assertThat(result.getValue()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    private BidResponse givenBidResponse(Function<Bid.BidBuilder, Bid.BidBuilder> bidCustomize) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder().bid(singletonList(bidCustomize.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static HttpCall<BidRequest> sampleHttpCall(BidRequest bidRequest, String body) {
        return HttpCall.success(
                HttpRequest.<BidRequest>builder().payload(bidRequest).build(),
                HttpResponse.of(200, null, body),
                null);
    }

    private static BidRequest givenBidRequest() {
        return BidRequest.builder()
                .id("request_id")
                .imp(singletonList(Imp.builder()
                        .id("imp_id")
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createObjectNode())))
                        .build()))
                .build();
    }

    private static BidRequest givenBidRequest(UnaryOperator<Imp.ImpBuilder> bidImpCustomize) {
        return BidRequest.builder()
                .imp(singletonList(bidImpCustomize.apply(Imp.builder()).build()))
                .build();
    }
}
