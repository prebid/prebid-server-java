package org.prebid.server.auction.model;

import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.bidder.model.BidderBid;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.entry;

public class BidRejectionTrackerTest {

    private BidRejectionTracker target;

    @BeforeEach
    public void setUp() {
        target = new BidRejectionTracker("bidder", singleton("impId1"), 0);
    }

    @Test
    public void succeedShouldRestoreImpFromImpRejection() {
        // given
        target.rejectImp("impId1", BidRejectionReason.ERROR_GENERAL);

        // when
        final BidderBid bid = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId1").build()).build();
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejectedImps()).isEmpty();
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(Pair.of(null, BidRejectionReason.ERROR_GENERAL))));
    }

    @Test
    public void succeedShouldRestoreImpFromBidRejection() {
        // given
        final BidderBid bid = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId1").build()).build();
        target.rejectBid(bid, BidRejectionReason.ERROR_GENERAL);

        // when
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejectedImps()).isEmpty();
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(Pair.of(bid, BidRejectionReason.ERROR_GENERAL))));
    }

    @Test
    public void succeedShouldIgnoreUninvolvedImpIdsOnImpRejection() {
        // given
        target.rejectImp("impId1", BidRejectionReason.ERROR_GENERAL);

        // when
        final BidderBid bid = BidderBid.builder().bid(Bid.builder().id("bidId2").impid("impId2").build()).build();
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejectedImps()).containsOnly(entry("impId1", BidRejectionReason.ERROR_GENERAL));
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(Pair.of(null, BidRejectionReason.ERROR_GENERAL))));
    }

    @Test
    public void succeedShouldIgnoreUninvolvedImpIdsOnBidRejection() {
        // given
        final BidderBid bid1 = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId1").build()).build();
        target.rejectBid(bid1, BidRejectionReason.ERROR_GENERAL);

        // when
        final BidderBid bid2 = BidderBid.builder().bid(Bid.builder().id("bidId2").impid("impId2").build()).build();
        target.succeed(singleton(bid2));

        // then
        assertThat(target.getRejectedImps()).containsOnly(entry("impId1", BidRejectionReason.ERROR_GENERAL));
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(Pair.of(bid1, BidRejectionReason.ERROR_GENERAL))));
    }

    @Test
    public void rejectImpShouldRecordImpRejectionFirstTimeIfImpIdIsInvolved() {
        // when
        target.rejectImp("impId1", BidRejectionReason.ERROR_GENERAL);

        // then
        assertThat(target.getRejectedImps()).containsOnly(entry("impId1", BidRejectionReason.ERROR_GENERAL));
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(Pair.of(null, BidRejectionReason.ERROR_GENERAL))));
    }

    @Test
    public void rejectBidShouldRecordBidRejectionFirstTimeIfImpIdIsInvolved() {
        // when
        final BidderBid bid = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId1").build()).build();
        target.rejectBid(bid, BidRejectionReason.ERROR_GENERAL);

        // then
        assertThat(target.getRejectedImps()).containsOnly(entry("impId1", BidRejectionReason.ERROR_GENERAL));
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(Pair.of(bid, BidRejectionReason.ERROR_GENERAL))));
    }

    @Test
    public void rejectBidShouldRecordBidRejectionAfterPreviouslySucceededBid() {
        // given
        final BidderBid bid1 = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId1").build()).build();
        final BidderBid bid2 = BidderBid.builder().bid(Bid.builder().id("bidId2").impid("impId1").build()).build();
        target.succeed(Set.of(bid1, bid2));

        // when
        target.rejectBid(bid1, BidRejectionReason.ERROR_GENERAL);

        // then
        assertThat(target.getRejectedImps()).isEmpty();
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(Pair.of(bid1, BidRejectionReason.ERROR_GENERAL))));
    }

    @Test
    public void rejectImpShouldNotRecordImpRejectionIfImpIdIsAlreadyRejected() {
        // given
        target.rejectImp("impId1", BidRejectionReason.ERROR_GENERAL);

        // when
        target.rejectImp("impId1", BidRejectionReason.ERROR_INVALID_BID_RESPONSE);

        // then
        assertThat(target.getRejectedImps()).containsOnly(entry("impId1", BidRejectionReason.ERROR_GENERAL));
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(
                        Pair.of(null, BidRejectionReason.ERROR_GENERAL),
                        Pair.of(null, BidRejectionReason.ERROR_INVALID_BID_RESPONSE))));
    }

    @Test
    public void rejectBidShouldNotRecordImpRejectionButRecordBidRejectionEvenIfImpIsAlreadyRejected() {
        // given
        final BidderBid bid1 = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId1").build()).build();
        target.rejectBid(bid1, BidRejectionReason.RESPONSE_REJECTED_GENERAL);

        // when
        final BidderBid bid2 = BidderBid.builder().bid(Bid.builder().id("bidId2").impid("impId1").build()).build();
        target.rejectBid(bid2, BidRejectionReason.RESPONSE_REJECTED_BELOW_FLOOR);

        // then
        assertThat(target.getRejectedImps())
                .containsOnly(entry("impId1", BidRejectionReason.RESPONSE_REJECTED_GENERAL));
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(
                        Pair.of(bid1, BidRejectionReason.RESPONSE_REJECTED_GENERAL),
                        Pair.of(bid2, BidRejectionReason.RESPONSE_REJECTED_BELOW_FLOOR))));
    }

    @Test
    public void rejectAllImpsShouldTryRejectingEachImpId() {
        // given
        target = new BidRejectionTracker("bidder", Set.of("impId1", "impId2", "impId3"), 0);
        target.rejectImp("impId1", BidRejectionReason.NO_BID);

        // when
        target.rejectAllImps(BidRejectionReason.ERROR_TIMED_OUT);

        // then
        assertThat(target.getRejectedImps())
                .isEqualTo(Map.of(
                        "impId1", BidRejectionReason.NO_BID,
                        "impId2", BidRejectionReason.ERROR_TIMED_OUT,
                        "impId3", BidRejectionReason.ERROR_TIMED_OUT));

        assertThat(target.getRejectedBids())
                .containsOnly(
                        entry("impId1", List.of(
                                Pair.of(null, BidRejectionReason.NO_BID),
                                Pair.of(null, BidRejectionReason.ERROR_TIMED_OUT))),
                        entry("impId2", List.of(Pair.of(null, BidRejectionReason.ERROR_TIMED_OUT))),
                        entry("impId3", List.of(Pair.of(null, BidRejectionReason.ERROR_TIMED_OUT))));
    }

    @Test
    public void rejectBidsShouldTryRejectingEachBid() {
        // given
        target = new BidRejectionTracker("bidder", Set.of("impId1", "impId2", "impId3"), 0);
        final BidderBid bid0 = BidderBid.builder().bid(Bid.builder().id("bidId0").impid("impId1").build()).build();
        target.rejectBid(bid0, BidRejectionReason.RESPONSE_REJECTED_GENERAL);

        // when
        final BidderBid bid1 = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId1").build()).build();
        final BidderBid bid2 = BidderBid.builder().bid(Bid.builder().id("bidId2").impid("impId2").build()).build();
        final BidderBid bid3 = BidderBid.builder().bid(Bid.builder().id("bidId3").impid("impId3").build()).build();
        target.rejectBids(Set.of(bid1, bid2, bid3), BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY);

        // then
        assertThat(target.getRejectedImps())
                .isEqualTo(Map.of(
                        "impId1", BidRejectionReason.RESPONSE_REJECTED_GENERAL,
                        "impId2", BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY,
                        "impId3", BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY));

        assertThat(target.getRejectedBids())
                .containsOnly(
                        entry("impId1", List.of(
                                Pair.of(bid0, BidRejectionReason.RESPONSE_REJECTED_GENERAL),
                                Pair.of(bid1, BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY))),
                        entry("impId2", List.of(Pair.of(bid2, BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY))),
                        entry("impId3", List.of(Pair.of(bid3, BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY))));
    }

    @Test
    public void getRejectedImpsShouldTreatUnsuccessfulImpsAsNoBidRejection() {
        // given
        target = new BidRejectionTracker("bidder", Set.of("impId1", "impId2"), 0);
        final BidderBid bid = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId2").build()).build();
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejectedImps()).containsOnly(entry("impId1", BidRejectionReason.NO_BID));
    }

    @Test
    public void rejectImpShouldFailRejectingWithReasonThatImpliesExistingBidToReject() {
        assertThatThrownBy(() -> target.rejectImp("impId1", BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The non-bid code 300 and higher assumes "
                        + "that there is a rejected bid that shouldn't be lost");
    }
}
