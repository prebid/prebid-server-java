package org.prebid.server.bidder.medianet;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.bidder.medianet.model.response.InterestGroupAuctionIntent;
import org.prebid.server.bidder.medianet.model.response.InterestGroupAuctionSeller;
import org.prebid.server.bidder.medianet.model.response.MedianetBidResponse;
import org.prebid.server.bidder.medianet.model.response.MedianetBidResponseExt;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.CompositeBidderResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.response.FledgeAuctionConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.prebid.server.proto.openrtb.ext.response.BidType.audio;
import static org.prebid.server.proto.openrtb.ext.response.BidType.banner;
import static org.prebid.server.proto.openrtb.ext.response.BidType.video;
import static org.prebid.server.proto.openrtb.ext.response.BidType.xNative;

public class MedianetBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.media.net?src=external.prebidserver.com";

    private final MedianetBidder target = new MedianetBidder(ENDPOINT_URL, jacksonMapper);

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new MedianetBidder("invalid_url", jacksonMapper));
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
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

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
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getBids()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnEmptyListIfBidResponseSeatBidIsNull() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall;
        httpCall = sampleHttpCall(null, mapper.writeValueAsString(BidResponse.builder().build()));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getBids()).isEmpty();
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnBannerBidIfBannerIsPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenBidRequest(),
                mapper.writeValueAsString(sampleBidResponse(bidBuilder -> bidBuilder.impid("123"))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getBids())
                .containsExactly(BidderBid.of(Bid.builder().impid("123").build(), banner, "USD"));
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void makeBidsShouldReturnCorrespondingMtypesAndAdTypes() throws JsonProcessingException {
        final List<Bid> bids = new ArrayList<>();
        final Bid bid1 = Bid.builder().impid("imp_id").mtype(1).build();
        final Bid bid2 = Bid.builder().impid("imp_id").mtype(2).build();
        final Bid bid3 = Bid.builder().impid("imp_id").mtype(3).build();
        final Bid bid4 = Bid.builder().impid("imp_id").mtype(4).build();
        bids.add(bid1);
        bids.add(bid2);
        bids.add(bid3);
        bids.add(bid4);

        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenBidRequest(),
                mapper.writeValueAsString(sampleMultiFormatBidResponse(bids)));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getBids()).hasSize(4);
        assertThat(result.getErrors()).isEmpty();
        final BidderBid bannerBid = BidderBid.of(bid1, banner, "USD");
        final BidderBid videoBid = BidderBid.of(bid2, video, "USD");
        final BidderBid audioBid = BidderBid.of(bid3, audio, "USD");
        final BidderBid xNativeBid = BidderBid.of(bid4, xNative, "USD");
        assertThat(result.getBids()).containsExactlyInAnyOrder(bannerBid, videoBid, audioBid, xNativeBid);
    }

    @Test
    public void makeBidsShouldReturnAdTypeAccordingToImpressionIfMtypeIsAbsent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenVideoBidRequest(),
                mapper.writeValueAsString(sampleBidResponse(bidBuilder -> bidBuilder.impid("imp_id"))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getBids()).hasSize(1);
        assertThat(result.getErrors()).isEmpty();
        final BidderBid videoBid = BidderBid.of(Bid.builder().impid("imp_id").build(), video, "USD");
        assertThat(result.getBids()).containsExactly(videoBid);
    }

    @Test
    public void makeBidsShouldReturnBannerAdTypeIfMtypeIsAbsentAndIfNoImpressionIdMatches()
            throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenVideoBidRequest(),
                mapper.writeValueAsString(sampleBidResponse(bidBuilder -> bidBuilder.impid("imp_id2"))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getBids()).hasSize(1);
        assertThat(result.getErrors()).isEmpty();
        final BidderBid bannerBid = BidderBid.of(Bid.builder().impid("imp_id2").build(), banner, "USD");
        assertThat(result.getBids()).containsExactly(bannerBid);
    }

    @Test
    public void makeBidsShouldReturnErrorIfMtypeIsWrong() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenVideoBidRequest(),
                mapper.writeValueAsString(sampleBidResponse(bidBuilder -> bidBuilder.impid("imp_id").mtype(5))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getBids()).isEmpty();
        assertThat(result.getErrors()).hasSize(1);
        final BidderError error = BidderError.badServerResponse("Unable to fetch mediaType: imp_id");
        assertThat(result.getErrors()).containsExactly(error);
    }

    @Test
    public void makeBidsShouldReturnFledgeConfigIfBidIsPresent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenBidRequest(),
                mapper.writeValueAsString(sampleBidResponseWithFledgeConfig(bidBuilder -> bidBuilder.impid("imp_id"))));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).hasSize(1);
        assertThat(result.getFledgeAuctionConfigs())
                .containsOnly(FledgeAuctionConfig.builder()
                        .impId("imp_id")
                        .config(mapper.createObjectNode().put("someKey", "someValue"))
                        .build());
    }

    @Test
    public void makeBidsShouldReturnFledgeConfigIfBidIsAbsent() throws JsonProcessingException {
        // given
        final BidderCall<BidRequest> httpCall = sampleHttpCall(
                givenBidRequest(),
                mapper.writeValueAsString(sampleBidResponseWithoutBidAndWithFledgeConfig("imp_id")));

        // when
        final CompositeBidderResponse result = target.makeBidderResponse(httpCall, null);

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getBids()).isEmpty();
        assertThat(result.getFledgeAuctionConfigs())
                .containsOnly(FledgeAuctionConfig.builder()
                        .impId("imp_id")
                        .config(mapper.createObjectNode().put("someKey", "someValue"))
                        .build());
    }

    private static MedianetBidResponse sampleBidResponse(Function<Bid.BidBuilder,
            Bid.BidBuilder> bidCustomizer) {
        return MedianetBidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bidCustomizer.apply(Bid.builder()).build()))
                        .build()))
                .build();
    }

    private static MedianetBidResponse sampleBidResponseWithFledgeConfig(Function<Bid.BidBuilder,
            Bid.BidBuilder> bidCustomizer) {
        final Bid bid = bidCustomizer.apply(Bid.builder()).build();
        return MedianetBidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(singletonList(bid))
                        .build()))
                .ext(MedianetBidResponseExt.of(InterestGroupAuctionIntent.builder()
                        .igs(List.of(InterestGroupAuctionSeller.builder()
                                .impId(bid.getImpid())
                                .config(mapper.createObjectNode().put("someKey", "someValue"))
                                .build()))
                        .build()))
                .build();
    }

    private static MedianetBidResponse sampleBidResponseWithoutBidAndWithFledgeConfig(String impId) {
        return MedianetBidResponse.builder()
                .cur("USD")
                .seatbid(emptyList())
                .ext(MedianetBidResponseExt.of(InterestGroupAuctionIntent.builder()
                        .igs(List.of(InterestGroupAuctionSeller.builder()
                                .impId(impId)
                                .config(mapper.createObjectNode().put("someKey", "someValue"))
                                .build()))
                        .build()))
                .build();
    }

    private static MedianetBidResponse sampleMultiFormatBidResponse(List<Bid> bids) {
        return MedianetBidResponse.builder()
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
