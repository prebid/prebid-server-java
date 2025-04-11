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
import static org.prebid.server.auction.model.BidRejectionReason.ERROR_GENERAL;
import static org.prebid.server.auction.model.BidRejectionReason.ERROR_INVALID_BID_RESPONSE;
import static org.prebid.server.auction.model.BidRejectionReason.ERROR_TIMED_OUT;
import static org.prebid.server.auction.model.BidRejectionReason.NO_BID;
import static org.prebid.server.auction.model.BidRejectionReason.RESPONSE_REJECTED_BELOW_FLOOR;
import static org.prebid.server.auction.model.BidRejectionReason.RESPONSE_REJECTED_DSA_PRIVACY;
import static org.prebid.server.auction.model.BidRejectionReason.RESPONSE_REJECTED_GENERAL;

public class BidRejectionTrackerTest {

    private BidRejectionTracker target;

    @BeforeEach
    public void setUp() {
        target = new BidRejectionTracker("bidder", singleton("impId1"), 0);
    }

    @Test
    public void succeedShouldRestoreImpFromImpRejection() {
        // given
        target.rejectImp("impId1", ERROR_GENERAL);

        // when
        final BidderBid bid = givenBid("bidId1", "impId1");
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejectedImps()).isEmpty();
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(Pair.of(null, ERROR_GENERAL))));
    }

    @Test
    public void succeedShouldRestoreImpFromBidRejection() {
        // given
        final BidderBid bid = givenBid("bidId1", "impId1");
        target.rejectBid(bid, ERROR_GENERAL);

        // when
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejectedImps()).isEmpty();
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(Pair.of(bid, ERROR_GENERAL))));
    }

    @Test
    public void succeedShouldIgnoreUninvolvedImpIdsOnImpRejection() {
        // given
        target.rejectImp("impId1", ERROR_GENERAL);

        // when
        final BidderBid bid = givenBid("bidId2", "impId2");
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejectedImps()).containsOnly(entry("impId1", Pair.of("bidder", ERROR_GENERAL)));
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(Pair.of(null, ERROR_GENERAL))));
    }

    @Test
    public void succeedShouldIgnoreUninvolvedImpIdsOnBidRejection() {
        // given
        final BidderBid bid1 = givenBid("bidId1", "impId1");
        target.rejectBid(bid1, ERROR_GENERAL);

        // when
        final BidderBid bid2 = givenBid("bidId2", "impId2");
        target.succeed(singleton(bid2));

        // then
        assertThat(target.getRejectedImps()).containsOnly(entry("impId1", Pair.of("seat", ERROR_GENERAL)));
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(Pair.of(bid1, ERROR_GENERAL))));
    }

    @Test
    public void rejectImpShouldRecordImpRejectionFirstTimeIfImpIdIsInvolved() {
        // when
        target.rejectImp("impId1", ERROR_GENERAL);

        // then
        assertThat(target.getRejectedImps()).containsOnly(entry("impId1", Pair.of("bidder", ERROR_GENERAL)));
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(Pair.of(null, ERROR_GENERAL))));
    }

    @Test
    public void rejectBidShouldRecordBidRejectionFirstTimeIfImpIdIsInvolved() {
        // when
        final BidderBid bid = givenBid("bidId1", "impId1");
        target.rejectBid(bid, ERROR_GENERAL);

        // then
        assertThat(target.getRejectedImps()).containsOnly(entry("impId1", Pair.of("seat", ERROR_GENERAL)));
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(Pair.of(bid, ERROR_GENERAL))));
    }

    @Test
    public void rejectBidShouldRecordBidRejectionAfterPreviouslySucceededBid() {
        // given
        final BidderBid bid1 = givenBid("bidId1", "impId1");
        final BidderBid bid2 = givenBid("bidId2", "impId1");
        target.succeed(Set.of(bid1, bid2));

        // when
        target.rejectBid(bid1, ERROR_GENERAL);

        // then
        assertThat(target.getRejectedImps()).isEmpty();
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(Pair.of(bid1, ERROR_GENERAL))));
    }

    @Test
    public void rejectImpShouldNotRecordImpRejectionIfImpIdIsAlreadyRejected() {
        // given
        target.rejectImp("impId1", ERROR_GENERAL);

        // when
        target.rejectImp("impId1", ERROR_INVALID_BID_RESPONSE);

        // then
        assertThat(target.getRejectedImps()).containsOnly(entry("impId1", Pair.of("bidder", ERROR_GENERAL)));
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(
                        Pair.of(null, ERROR_GENERAL),
                        Pair.of(null, ERROR_INVALID_BID_RESPONSE))));
    }

    @Test
    public void rejectBidShouldNotRecordImpRejectionButRecordBidRejectionEvenIfImpIsAlreadyRejected() {
        // given
        final BidderBid bid1 = givenBid("bidId1", "impId1");
        target.rejectBid(bid1, RESPONSE_REJECTED_GENERAL);

        // when
        final BidderBid bid2 = givenBid("bidId2", "impId1");
        target.rejectBid(bid2, RESPONSE_REJECTED_BELOW_FLOOR);

        // then
        assertThat(target.getRejectedImps())
                .containsOnly(entry("impId1", Pair.of("seat", RESPONSE_REJECTED_GENERAL)));
        assertThat(target.getRejectedBids())
                .containsOnly(entry("impId1", List.of(
                        Pair.of(bid1, RESPONSE_REJECTED_GENERAL),
                        Pair.of(bid2, RESPONSE_REJECTED_BELOW_FLOOR))));
    }

    @Test
    public void rejectAllImpsShouldTryRejectingEachImpId() {
        // given
        target = new BidRejectionTracker("bidder", Set.of("impId1", "impId2", "impId3"), 0);
        target.rejectImp("impId1", NO_BID);

        // when
        target.rejectAllImps(ERROR_TIMED_OUT);

        // then
        assertThat(target.getRejectedImps())
                .isEqualTo(Map.of(
                        "impId1", Pair.of("bidder", NO_BID),
                        "impId2", Pair.of("bidder", ERROR_TIMED_OUT),
                        "impId3", Pair.of("bidder", ERROR_TIMED_OUT)));

        assertThat(target.getRejectedBids())
                .containsOnly(
                        entry("impId1", List.of(
                                Pair.of(null, NO_BID),
                                Pair.of(null, ERROR_TIMED_OUT))),
                        entry("impId2", List.of(Pair.of(null, ERROR_TIMED_OUT))),
                        entry("impId3", List.of(Pair.of(null, ERROR_TIMED_OUT))));
    }

    @Test
    public void rejectBidsShouldTryRejectingEachBid() {
        // given
        target = new BidRejectionTracker("bidder", Set.of("impId1", "impId2", "impId3"), 0);
        final BidderBid bid0 = givenBid("bidId0", "impId1");
        target.rejectBid(bid0, RESPONSE_REJECTED_GENERAL);

        // when
        final BidderBid bid1 = givenBid("bidId1", "impId1");
        final BidderBid bid2 = givenBid("bidId2", "impId2");
        final BidderBid bid3 = givenBid("bidId3", "impId3");
        target.rejectBids(Set.of(bid1, bid2, bid3), RESPONSE_REJECTED_DSA_PRIVACY);

        // then
        assertThat(target.getRejectedImps())
                .isEqualTo(Map.of(
                        "impId1", Pair.of("seat", RESPONSE_REJECTED_GENERAL),
                        "impId2", Pair.of("seat", RESPONSE_REJECTED_DSA_PRIVACY),
                        "impId3", Pair.of("seat", RESPONSE_REJECTED_DSA_PRIVACY)));

        assertThat(target.getRejectedBids())
                .containsOnly(
                        entry("impId1", List.of(
                                Pair.of(bid0, RESPONSE_REJECTED_GENERAL),
                                Pair.of(bid1, RESPONSE_REJECTED_DSA_PRIVACY))),
                        entry("impId2", List.of(Pair.of(bid2, RESPONSE_REJECTED_DSA_PRIVACY))),
                        entry("impId3", List.of(Pair.of(bid3, RESPONSE_REJECTED_DSA_PRIVACY))));
    }

    @Test
    public void getRejectedImpsShouldTreatUnsuccessfulImpsAsNoBidRejection() {
        // given
        target = new BidRejectionTracker("bidder", Set.of("impId1", "impId2"), 0);
        final BidderBid bid = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId2").build()).build();
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejectedImps()).containsOnly(entry("impId1", Pair.of("bidder", NO_BID)));
    }

    @Test
    public void rejectImpShouldFailRejectingWithReasonThatImpliesExistingBidToReject() {
        assertThatThrownBy(() -> target.rejectImp("impId1", RESPONSE_REJECTED_DSA_PRIVACY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The non-bid code 300 and higher assumes "
                        + "that there is a rejected bid that shouldn't be lost");
    }

    private BidderBid givenBid(String bidId, String impId) {
        return BidderBid.builder()
                .seat("seat")
                .bid(Bid.builder().id(bidId).impid(impId).build())
                .build();
    }
}
