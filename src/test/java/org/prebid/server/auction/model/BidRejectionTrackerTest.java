package org.prebid.server.auction.model;

import com.iab.openrtb.response.Bid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.prebid.server.bidder.model.BidderBid;

import java.util.List;
import java.util.Set;

import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
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
        target.reject(RejectedImp.of("impId1", ERROR_GENERAL));

        // when
        final BidderBid bid = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId1").build()).build();
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejectedImps()).isEmpty();
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(RejectedImp.of("impId1", ERROR_GENERAL))));
    }

    @Test
    public void succeedShouldRestoreImpFromBidRejection() {
        // given
        final BidderBid bid = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId1").build()).build();
        target.reject(RejectedBid.of(bid, ERROR_GENERAL));

        // when
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejectedImps()).isEmpty();
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(RejectedBid.of(bid, ERROR_GENERAL))));
    }

    @Test
    public void succeedShouldIgnoreUninvolvedImpIdsOnImpRejection() {
        // given
        target.reject(RejectedImp.of("impId1", ERROR_GENERAL));

        // when
        final BidderBid bid = BidderBid.builder().bid(Bid.builder().id("bidId2").impid("impId2").build()).build();
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejectedImps()).containsOnly(RejectedImp.of("impId1", ERROR_GENERAL));
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(RejectedImp.of("impId1", ERROR_GENERAL))));
    }

    @Test
    public void succeedShouldIgnoreUninvolvedImpIdsOnBidRejection() {
        // given
        final BidderBid bid1 = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId1").build()).build();
        target.reject(RejectedBid.of(bid1, ERROR_GENERAL));

        // when
        final BidderBid bid2 = BidderBid.builder().bid(Bid.builder().id("bidId2").impid("impId2").build()).build();
        target.succeed(singleton(bid2));

        // then
        assertThat(target.getRejectedImps()).containsOnly(RejectedImp.of("impId1", ERROR_GENERAL));
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(RejectedBid.of(bid1, ERROR_GENERAL))));
    }

    @Test
    public void rejectImpShouldRecordImpRejectionFirstTimeIfImpIdIsInvolved() {
        // when
        target.reject(RejectedImp.of("impId1", ERROR_GENERAL));

        // then
        assertThat(target.getRejectedImps()).containsOnly(RejectedImp.of("impId1", ERROR_GENERAL));
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(RejectedImp.of("impId1", ERROR_GENERAL))));
    }

    @Test
    public void rejectBidShouldRecordBidRejectionFirstTimeIfImpIdIsInvolved() {
        // when
        final BidderBid bid = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId1").build()).build();
        target.reject(RejectedBid.of(bid, ERROR_GENERAL));

        // then
        assertThat(target.getRejectedImps()).containsOnly(RejectedImp.of("impId1", ERROR_GENERAL));
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(RejectedBid.of(bid, ERROR_GENERAL))));
    }

    @Test
    public void rejectBidShouldRecordBidRejectionAfterPreviouslySucceededBid() {
        // given
        final BidderBid bid1 = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId1").build()).build();
        final BidderBid bid2 = BidderBid.builder().bid(Bid.builder().id("bidId2").impid("impId1").build()).build();
        target.succeed(Set.of(bid1, bid2));

        // when
        target.reject(RejectedBid.of(bid1, ERROR_GENERAL));

        // then
        assertThat(target.getRejectedImps()).isEmpty();
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(RejectedBid.of(bid1, ERROR_GENERAL))));
    }

    @Test
    public void rejectImpShouldNotRecordImpRejectionIfImpIdIsAlreadyRejected() {
        // given
        target.reject(RejectedImp.of("impId1", ERROR_GENERAL));

        // when
        target.reject(RejectedImp.of("impId1", ERROR_INVALID_BID_RESPONSE));

        // then
        assertThat(target.getRejectedImps()).containsOnly(RejectedImp.of("impId1", ERROR_GENERAL));
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(
                        RejectedImp.of("impId1", ERROR_GENERAL),
                        RejectedImp.of("impId1", ERROR_INVALID_BID_RESPONSE))));
    }

    @Test
    public void rejectBidShouldNotRecordImpRejectionButRecordBidRejectionEvenIfImpIsAlreadyRejected() {
        // given
        final BidderBid bid1 = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId1").build()).build();
        target.reject(RejectedBid.of(bid1, RESPONSE_REJECTED_GENERAL));

        // when
        final BidderBid bid2 = BidderBid.builder().bid(Bid.builder().id("bidId2").impid("impId1").build()).build();
        target.reject(RejectedBid.of(bid2, RESPONSE_REJECTED_BELOW_FLOOR));

        // then
        assertThat(target.getRejectedImps())
                .containsOnly(RejectedImp.of("impId1", RESPONSE_REJECTED_GENERAL));
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(
                        RejectedBid.of(bid1, RESPONSE_REJECTED_GENERAL),
                        RejectedBid.of(bid2, RESPONSE_REJECTED_BELOW_FLOOR))));
    }

    @Test
    public void rejectAllShouldTryRejectingEachImpId() {
        // given
        target = new BidRejectionTracker("bidder", Set.of("impId1", "impId2", "impId3"), 0);
        target.reject(RejectedImp.of("impId1", NO_BID));

        // when
        target.rejectAll(ERROR_TIMED_OUT);

        // then
        assertThat(target.getRejectedImps())
                .containsOnly(
                        RejectedImp.of("impId1", NO_BID),
                        RejectedImp.of("impId2", ERROR_TIMED_OUT),
                        RejectedImp.of("impId3", ERROR_TIMED_OUT));

        assertThat(target.getAllRejected())
                .containsOnly(
                        entry("impId1", List.of(
                                RejectedImp.of("impId1", NO_BID),
                                RejectedImp.of("impId1", ERROR_TIMED_OUT))),
                        entry("impId2", List.of(RejectedImp.of("impId2", ERROR_TIMED_OUT))),
                        entry("impId3", List.of(RejectedImp.of("impId3", ERROR_TIMED_OUT))));
    }

    @Test
    public void rejectBidsShouldTryRejectingEachBid() {
        // given
        target = new BidRejectionTracker("bidder", Set.of("impId1", "impId2", "impId3"), 0);
        final BidderBid bid0 = BidderBid.builder().bid(Bid.builder().id("bidId0").impid("impId1").build()).build();
        target.reject(RejectedBid.of(bid0, RESPONSE_REJECTED_GENERAL));

        // when
        final BidderBid bid1 = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId1").build()).build();
        final BidderBid bid2 = BidderBid.builder().bid(Bid.builder().id("bidId2").impid("impId2").build()).build();
        final BidderBid bid3 = BidderBid.builder().bid(Bid.builder().id("bidId3").impid("impId3").build()).build();
        target.reject(Set.of(
                RejectedBid.of(bid1, RESPONSE_REJECTED_DSA_PRIVACY),
                RejectedBid.of(bid2, RESPONSE_REJECTED_DSA_PRIVACY),
                RejectedBid.of(bid3, RESPONSE_REJECTED_DSA_PRIVACY)));

        // then
        assertThat(target.getRejectedImps())
                .containsOnly(
                        RejectedImp.of("impId1", RESPONSE_REJECTED_GENERAL),
                        RejectedImp.of("impId2", RESPONSE_REJECTED_DSA_PRIVACY),
                        RejectedImp.of("impId3", RESPONSE_REJECTED_DSA_PRIVACY));

        assertThat(target.getAllRejected())
                .containsOnly(
                        entry("impId1", List.of(
                                RejectedBid.of(bid0, RESPONSE_REJECTED_GENERAL),
                                RejectedBid.of(bid1, RESPONSE_REJECTED_DSA_PRIVACY))),
                        entry("impId2", List.of(RejectedBid.of(bid2, RESPONSE_REJECTED_DSA_PRIVACY))),
                        entry("impId3", List.of(RejectedBid.of(bid3, RESPONSE_REJECTED_DSA_PRIVACY))));
    }

    @Test
    public void getRejectedImpsShouldTreatUnsuccessfulImpsAsNoBidRejection() {
        // given
        target = new BidRejectionTracker("bidder", Set.of("impId1", "impId2"), 0);
        final BidderBid bid = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId2").build()).build();
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejectedImps()).containsOnly(RejectedImp.of("impId1", NO_BID));
    }
}
