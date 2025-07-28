package org.prebid.server.auction.adpodding;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Video;
import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAdPoddingConfig;
import org.prebid.server.settings.model.AccountAuctionConfig;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.UnaryOperator;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class AdPoddingBidDeduplicationServiceTest extends VertxTest {

    private final AdPoddingBidDeduplicationService target = new AdPoddingBidDeduplicationService(jacksonMapper);

    @Mock
    private BidRejectionTracker bidRejectionTracker;

    @Test
    public void deduplicateShouldReturnOriginalParticipationWhenDeduplicationIsDisabledInAccount() {
        // given
        final Account account = givenAccount(false);
        final AuctionParticipation originalParticipation = givenAuctionParticipation(emptyList());

        // when
        final AuctionParticipation result = target.deduplicate(
                BidRequest.builder().build(), originalParticipation, account, bidRejectionTracker);

        // then
        assertThat(result).isSameAs(originalParticipation);
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void deduplicateShouldReturnOriginalParticipationWhenAdPodConfigIsMissing() {
        // given
        final Account account = Account.builder()
                                        .auction(AccountAuctionConfig.builder().build())
                                        .build();
        final AuctionParticipation originalParticipation = givenAuctionParticipation(emptyList());

        // when
        final AuctionParticipation result = target.deduplicate(
                BidRequest.builder().build(), originalParticipation, account, bidRejectionTracker);

        // then
        assertThat(result).isSameAs(originalParticipation);
        verifyNoInteractions(bidRejectionTracker);
    }

    @Test
    public void deduplicateShouldCorrectlyDeduplicateVideoBidsAndKeepTheHighestCpm() {
        // given
        final Imp imp = givenVideoImp("impId");
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final BidderBid losingBid1 = givenVideoBid("impId-0", BigDecimal.valueOf(2.0), 10);
        final BidderBid winningBid = givenVideoBid("impId-1", BigDecimal.valueOf(5.0), 20);
        final BidderBid losingBid2 = givenVideoBid("impId-2", BigDecimal.valueOf(3.0), 15);

        final AuctionParticipation participation = givenAuctionParticipation(
                List.of(losingBid1, winningBid, losingBid2));

        // when
        final AuctionParticipation result = target.deduplicate(
                bidRequest,
                participation,
                givenAccount(true),
                bidRejectionTracker);

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids())
                .containsExactly(winningBid);
        verify(bidRejectionTracker).rejectBid(eq(losingBid1), eq(BidRejectionReason.RESPONSE_REJECTED_DUPLICATE));
        verify(bidRejectionTracker).rejectBid(eq(losingBid2), eq(BidRejectionReason.RESPONSE_REJECTED_DUPLICATE));
    }

    @Test
    public void deduplicateShouldUseMetaDurationWhenBidDurationIsMissing() {
        // given
        final Imp imp = givenVideoImp("impId");
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final BidderBid losingBid = givenVideoBid("impId-0", BigDecimal.valueOf(2.0), 10);
        final BidderBid winningBid = givenVideoBidWithMetaDur("impId-1", BigDecimal.valueOf(6.0), 20);

        final AuctionParticipation participation = givenAuctionParticipation(List.of(losingBid, winningBid));

        // when
        final AuctionParticipation result = target.deduplicate(
                bidRequest,
                participation,
                givenAccount(true),
                bidRejectionTracker);

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids()).containsExactly(winningBid);
        verify(bidRejectionTracker).rejectBid(eq(losingBid), eq(BidRejectionReason.RESPONSE_REJECTED_DUPLICATE));
    }

    @Test
    public void deduplicateShouldCorrectlyDeduplicateAudioBids() {
        // given
        final Imp imp = givenAudioImp("impId1");
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final BidderBid losingBid = givenAudioBid("impId1-0", BigDecimal.valueOf(1.0), 5);
        final BidderBid winningBid = givenAudioBid("impId1-1", BigDecimal.valueOf(4.0), 10);

        final AuctionParticipation participation = givenAuctionParticipation(List.of(losingBid, winningBid));

        // when
        final AuctionParticipation result = target.deduplicate(
                bidRequest,
                participation,
                givenAccount(true),
                bidRejectionTracker);

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids()).containsExactly(winningBid);
        verify(bidRejectionTracker).rejectBid(eq(losingBid), eq(BidRejectionReason.RESPONSE_REJECTED_DUPLICATE));
    }

    @Test
    public void deduplicateShouldHandleMultiplePodsAndMediaTypesCorrectly() {
        // given
        final Imp videoImp = givenVideoImp("videoImp");
        final Imp audioImp = givenAudioImp("audioImp");
        final BidRequest bidRequest = BidRequest.builder().imp(List.of(videoImp, audioImp)).build();

        final BidderBid videoWinner = givenVideoBid("videoImp-0", BigDecimal.valueOf(10.0), 20);
        final BidderBid videoLoser = givenVideoBid("videoImp-1", BigDecimal.valueOf(5.0), 20);

        final BidderBid audioWinner = givenAudioBid("audioImp-0", BigDecimal.valueOf(8.0), 10);
        final BidderBid audioLoser = givenAudioBid("audioImp-1", BigDecimal.valueOf(6.0), 10);

        final BidderBid bannerBid = givenBid(BidType.banner, identity());

        final AuctionParticipation participation = givenAuctionParticipation(
                List.of(videoWinner, videoLoser, audioWinner, audioLoser, bannerBid));

        // when
        final AuctionParticipation result = target.deduplicate(
                bidRequest,
                participation,
                givenAccount(true),
                bidRejectionTracker);

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids())
                .containsExactlyInAnyOrder(videoWinner, audioWinner, bannerBid);
        verify(bidRejectionTracker).rejectBid(eq(videoLoser), eq(BidRejectionReason.RESPONSE_REJECTED_DUPLICATE));
        verify(bidRejectionTracker).rejectBid(eq(audioLoser), eq(BidRejectionReason.RESPONSE_REJECTED_DUPLICATE));
    }

    @Test
    public void deduplicateShouldKeepFirstBidInCaseOfTieInCpm() {
        // given
        final Imp imp = givenVideoImp("impId");
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final BidderBid firstBid = givenVideoBid("impId-0", BigDecimal.valueOf(2.0), 10);
        final BidderBid secondBid = givenVideoBid("impId-1", BigDecimal.valueOf(4.0), 20);

        final AuctionParticipation participation = givenAuctionParticipation(List.of(firstBid, secondBid));

        // when
        final AuctionParticipation result = target.deduplicate(
                bidRequest,
                participation,
                givenAccount(true),
                bidRejectionTracker);

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids()).containsExactly(firstBid);
        verify(bidRejectionTracker).rejectBid(eq(secondBid), eq(BidRejectionReason.RESPONSE_REJECTED_DUPLICATE));
    }

    @Test
    public void deduplicateShouldHandleSingleImpWithBothVideoAndAudioPods() {
        // given
        final Imp imp = Imp.builder().id("imp")
                                .video(Video.builder().podid(1).build())
                                .audio(Audio.builder().podid(2).build())
                                .build();
        final BidRequest bidRequest = BidRequest.builder().imp(singletonList(imp)).build();

        final BidderBid videoWinner = givenVideoBid("imp-1", BigDecimal.valueOf(10.0), 20);
        final BidderBid videoLoser = givenVideoBid("imp-2", BigDecimal.valueOf(5.0), 20);

        final BidderBid audioWinner = givenAudioBid("imp-3", BigDecimal.valueOf(8.0), 10);
        final BidderBid audioLoser = givenAudioBid("imp-4", BigDecimal.valueOf(6.0), 10);

        final AuctionParticipation participation = givenAuctionParticipation(
                List.of(videoWinner, videoLoser, audioWinner, audioLoser));

        // when
        final AuctionParticipation result = target.deduplicate(
                bidRequest,
                participation,
                givenAccount(true),
                bidRejectionTracker);

        // then
        assertThat(result.getBidderResponse().getSeatBid().getBids())
                .containsExactlyInAnyOrder(videoWinner, audioWinner);

        verify(bidRejectionTracker).rejectBid(eq(videoLoser), eq(BidRejectionReason.RESPONSE_REJECTED_DUPLICATE));
        verify(bidRejectionTracker).rejectBid(eq(audioLoser), eq(BidRejectionReason.RESPONSE_REJECTED_DUPLICATE));
    }

    private static AuctionParticipation givenAuctionParticipation(List<BidderBid> bids) {
        return AuctionParticipation.builder()
                       .bidderResponse(BidderResponse.of("bidder", BidderSeatBid.of(bids), 100))
                       .build();
    }

    private static Imp givenVideoImp(String id) {
        return Imp.builder()
                       .id(id)
                       .video(Video.builder().podid(1).build())
                       .build();
    }

    private static Imp givenAudioImp(String id) {
        return Imp.builder()
                       .id(id)
                       .audio(Audio.builder().podid(1).build())
                       .build();
    }

    private static BidderBid givenVideoBid(String impId, BigDecimal price, int duration) {
        return givenBid(BidType.video, builder -> builder.impid(impId).price(price).dur(duration));
    }

    private static BidderBid givenAudioBid(String impId, BigDecimal price, int duration) {
        return givenBid(BidType.audio, builder -> builder.impid(impId).price(price).dur(duration));
    }

    private static BidderBid givenBid(BidType type, UnaryOperator<Bid.BidBuilder> bidCustomizer) {
        final Bid.BidBuilder bidBuilder = Bid.builder()
                                                  .id("bidId")
                                                  .impid("impId")
                                                  .crid("crid");

        return BidderBid.of(bidCustomizer.apply(bidBuilder).build(), type, "USD");
    }

    private Account givenAccount(boolean deduplicate) {
        return Account.builder()
                       .auction(AccountAuctionConfig.builder()
                                        .adPodding(AccountAdPoddingConfig.of(deduplicate))
                                        .build())
                       .build();
    }

    private BidderBid givenVideoBidWithMetaDur(String impId, BigDecimal price, int duration) {
        final ObjectNode bidExt = mapper.createObjectNode();
        final ObjectNode prebidNode = mapper.createObjectNode();
        final ObjectNode metaNode = mapper.createObjectNode();
        metaNode.put("dur", duration);
        prebidNode.set("meta", metaNode);
        bidExt.set("prebid", prebidNode);

        return givenBid(BidType.video, builder -> builder.impid(impId).price(price).dur(null).ext(bidExt));
    }
}
