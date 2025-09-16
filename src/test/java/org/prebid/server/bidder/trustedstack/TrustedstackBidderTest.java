package org.prebid.server.bidder.trustedstack;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class TrustedstackBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.trustedstack.com?src={{PREBID_SERVER_ENDPOINT}}";
    private static final String EXTERNAL_URL = "external.prebidserver.com";

    private final TrustedstackBidder target = new TrustedstackBidder(ENDPOINT_URL, EXTERNAL_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new TrustedstackBidder("invalid_url", EXTERNAL_URL, jacksonMapper));
    }

    @Test
    public void makeHttpRequestsShouldNotModifyIncomingRequest() {
        // given
        final BidRequest bidRequest = givenBidRequest();

        // when
        final Result<List<HttpRequest<BidRequest>>> result;
        result = target.makeHttpRequests(bidRequest);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1)
                .extracting(httpRequest -> mapper.readValue(httpRequest.getBody(), BidRequest.class))
                .containsExactly(bidRequest);
    }

    @Test
    public void makeBidsShouldReturnErrorIfResponseBodyCouldNotBeParsed() {
        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(givenBidRequest(), "invalid response");

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .allMatch(error -> error.getType() == BidderError.Type.bad_server_response
                        && error.getMessage().startsWith("Failed to decode: Unrecognized token"));
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall;
        httpCall = sampleHttpCall(givenBidRequest(), mapper.writeValueAsString(null));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall;
        httpCall = sampleHttpCall(null, mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenBidRequest(),
                mapper.writeValueAsString(sampleBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
    }

    @Test
    public void makeBidsShouldReturnBannerMtypeAndAdType() throws JsonProcessingException {
        final List<Bid> bids = new ArrayList<>();
        final Bid bid = Bid.builder().impid("imp_id").mtype(1).build();
        bids.add(bid);

        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenBidRequest(),
                mapper.writeValueAsString(sampleMultiFormatBidResponse(bids)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        final BidderBid bannerBid = BidderBid.of(bid, banner, "USD");
        assertThat(result.getValue()).containsExactlyInAnyOrder(bannerBid);
    }

    @Test
    public void makeBidsShouldReturnVideoMtypeAndAdType() throws JsonProcessingException {
        final List<Bid> bids = new ArrayList<>();
        final Bid bid = Bid.builder().impid("imp_id").mtype(2).build();
        bids.add(bid);

        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenBidRequest(),
                mapper.writeValueAsString(sampleMultiFormatBidResponse(bids)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        final BidderBid videoBid = BidderBid.of(bid, video, "USD");
        assertThat(result.getValue()).containsExactlyInAnyOrder(videoBid);
    }

    @Test
    public void makeBidsShouldReturnAudioMtypeAndAdType() throws JsonProcessingException {
        final List<Bid> bids = new ArrayList<>();
        final Bid bid = Bid.builder().impid("imp_id").mtype(3).build();
        bids.add(bid);

        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenBidRequest(),
                mapper.writeValueAsString(sampleMultiFormatBidResponse(bids)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        final BidderBid audioBid = BidderBid.of(bid, audio, "USD");
        assertThat(result.getValue()).containsExactlyInAnyOrder(audioBid);
    }

    @Test
    public void makeBidsShouldReturnNativeMtypeAndAdType() throws JsonProcessingException {
        final List<Bid> bids = new ArrayList<>();
        final Bid bid = Bid.builder().impid("imp_id").mtype(4).build();
        bids.add(bid);

        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenBidRequest(),
                mapper.writeValueAsString(sampleMultiFormatBidResponse(bids)));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        final BidderBid xNativeBid = BidderBid.of(bid, xNative, "USD");
        assertThat(result.getValue()).containsExactlyInAnyOrder(xNativeBid);
    }

    @Test
    public void makeBidsShouldReturnAdTypeAccordingToImpressionIfMtypeIsAbsent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenVideoBidRequest(),
                mapper.writeValueAsString(sampleBidResponse(bidBuilder -> bidBuilder.impid("imp_id"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        final BidderBid videoBid = BidderBid.of(Bid.builder().impid("imp_id").build(), video, "USD");
        assertThat(result.getValue()).containsExactly(videoBid);
    }

    @Test
    public void makeBidsShouldReturnBannerAdTypeIfMtypeIsAbsentAndIfNoImpressionIdMatches()
            throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenVideoBidRequest(),
                mapper.writeValueAsString(sampleBidResponse(bidBuilder -> bidBuilder.impid("imp_id2"))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getValue()).hasSize(1);
        final BidderBid bannerBid = BidderBid.of(Bid.builder().impid("imp_id2").build(), banner, "USD");
        assertThat(result.getValue()).containsExactly(bannerBid);
    }

    @Test
    public void makeBidsShouldReturnErrorIfMtypeIsWrong() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenVideoBidRequest(),
                mapper.writeValueAsString(sampleBidResponse(bidBuilder -> bidBuilder.impid("imp_id").mtype(5))));

        // when
        final Result<List<BidderBid>> result = target.makeBids(httpCall, null);

        // then
        assertThat(result.getErrors()).hasSize(1);
        assertThat(result.getValue()).isEmpty();
        final BidderError error = BidderError.badServerResponse("Unable to fetch mediaType: imp_id");
        assertThat(result.getErrors()).containsExactly(error);
    }

    private static BidResponse sampleBidResponse(Function<Bid.BidBuilder,
            Bid.BidBuilder> bidCustomizer) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static BidResponse sampleMultiFormatBidResponse(List<Bid> bids) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(bids)
                        .build()))
                .build();
    }

    private static BidderCall<BidRequest> sampleHttpCall(BidRequest bidRequest, String body) {
        return BidderCall.succeededHttp(
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

    private static BidRequest givenVideoBidRequest() {
        return BidRequest.builder()
                .id("request_id")
                .imp(singletonList(Imp.builder()
                        .id("imp_id")
                        .video(Video.builder().build())
                        .ext(mapper.valueToTree(ExtPrebid.of(null, mapper.createObjectNode())))
                        .build()))
                .build();
    }
}
