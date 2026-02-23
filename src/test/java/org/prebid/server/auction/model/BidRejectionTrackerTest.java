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
import static org.assertj.core.api.Assertions.tuple;
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
        target.reject(ImpRejection.of("impId1", ERROR_GENERAL));

        // when
        final BidderBid bid = givenBid("bidId1", "impId1");
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejected()).isEmpty();
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(ImpRejection.of("bidder", "impId1", ERROR_GENERAL))));
    }

    @Test
    public void succeedShouldRestoreImpFromBidRejection() {
        // given
        final BidderBid bid = givenBid("bidId1", "impId1");
        target.reject(BidRejection.of(bid, ERROR_GENERAL));

        // when
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejected()).isEmpty();
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(BidRejection.of(bid, ERROR_GENERAL))));
    }

    @Test
    public void succeedShouldIgnoreUninvolvedImpIdsOnImpRejection() {
        // given
        target.reject(ImpRejection.of("impId1", ERROR_GENERAL));

        // when
        final BidderBid bid = givenBid("bidId2", "impId2");
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejected()).extracting(Rejection::seat, Rejection::impId, Rejection::reason)
                .containsOnly(tuple("bidder", "impId1", ERROR_GENERAL));
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(ImpRejection.of("bidder", "impId1", ERROR_GENERAL))));
    }

    @Test
    public void succeedShouldIgnoreUninvolvedImpIdsOnBidRejection() {
        // given
        final BidderBid bid1 = givenBid("bidId1", "impId1");
        target.reject(BidRejection.of(bid1, ERROR_GENERAL));

        // when
        final BidderBid bid2 = givenBid("bidId2", "impId2");
        target.succeed(singleton(bid2));

        // then
        assertThat(target.getRejected()).extracting(Rejection::seat, Rejection::impId, Rejection::reason)
                .containsOnly(tuple("seat", "impId1", ERROR_GENERAL));
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(BidRejection.of(bid1, ERROR_GENERAL))));
    }

    @Test
    public void rejectImpShouldRecordImpRejectionFirstTimeIfImpIdIsInvolved() {
        // when
        target.reject(ImpRejection.of("impId1", ERROR_GENERAL));

        // then
        assertThat(target.getRejected()).extracting(Rejection::seat, Rejection::impId, Rejection::reason)
                .containsOnly(tuple("bidder", "impId1", ERROR_GENERAL));
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(ImpRejection.of("bidder", "impId1", ERROR_GENERAL))));
    }

    @Test
    public void rejectBidShouldRecordBidRejectionFirstTimeIfImpIdIsInvolved() {
        // when
        final BidderBid bid = givenBid("bidId1", "impId1");
        target.reject(BidRejection.of(bid, ERROR_GENERAL));

        // then
        assertThat(target.getRejected()).extracting(Rejection::seat, Rejection::impId, Rejection::reason)
                .containsOnly(tuple("seat", "impId1", ERROR_GENERAL));
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(BidRejection.of(bid, ERROR_GENERAL))));
    }

    @Test
    public void rejectBidShouldRecordBidRejectionAfterPreviouslySucceededBid() {
        // given
        final BidderBid bid1 = givenBid("bidId1", "impId1");
        final BidderBid bid2 = givenBid("bidId2", "impId1");
        target.succeed(Set.of(bid1, bid2));

        // when
        target.reject(BidRejection.of(bid1, ERROR_GENERAL));

        // then
        assertThat(target.getRejected()).isEmpty();
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(BidRejection.of(bid1, ERROR_GENERAL))));
    }

    @Test
    public void rejectImpShouldNotRecordImpRejectionIfImpIdIsAlreadyRejected() {
        // given
        target.reject(ImpRejection.of("impId1", ERROR_GENERAL));

        // when
        target.reject(ImpRejection.of("impId1", ERROR_INVALID_BID_RESPONSE));

        // then
        assertThat(target.getRejected()).extracting(Rejection::seat, Rejection::impId, Rejection::reason)
                .containsOnly(tuple("bidder", "impId1", ERROR_GENERAL));
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(
                        ImpRejection.of("bidder", "impId1", ERROR_GENERAL),
                        ImpRejection.of("bidder", "impId1", ERROR_INVALID_BID_RESPONSE))));
    }

    @Test
    public void rejectBidShouldNotRecordImpRejectionButRecordBidRejectionEvenIfImpIsAlreadyRejected() {
        // given
        final BidderBid bid1 = givenBid("bidId1", "impId1");
        target.reject(BidRejection.of(bid1, RESPONSE_REJECTED_GENERAL));

        // when
        final BidderBid bid2 = givenBid("bidId2", "impId1");
        target.reject(BidRejection.of(bid2, RESPONSE_REJECTED_BELOW_FLOOR));

        // then
        assertThat(target.getRejected()).extracting(Rejection::seat, Rejection::impId, Rejection::reason)
                .containsOnly(tuple("seat", "impId1", RESPONSE_REJECTED_GENERAL));
        assertThat(target.getAllRejected())
                .containsOnly(entry("impId1", List.of(
                        BidRejection.of(bid1, RESPONSE_REJECTED_GENERAL),
                        BidRejection.of(bid2, RESPONSE_REJECTED_BELOW_FLOOR))));
    }

    @Test
    public void rejectAllShouldTryRejectingEachImpId() {
        // given
        target = new BidRejectionTracker("bidder", Set.of("impId1", "impId2", "impId3"), 0);
        target.reject(ImpRejection.of("impId1", NO_BID));

        // when
        target.rejectAll(ERROR_TIMED_OUT);

        // then
        assertThat(target.getRejected()).extracting(Rejection::seat, Rejection::impId, Rejection::reason)
                .containsOnly(
                        tuple("bidder", "impId1", NO_BID),
                        tuple("bidder", "impId2", ERROR_TIMED_OUT),
                        tuple("bidder", "impId3", ERROR_TIMED_OUT));

        assertThat(target.getAllRejected())
                .containsOnly(
                        entry("impId1", List.of(
                                ImpRejection.of("bidder", "impId1", NO_BID),
                                ImpRejection.of("bidder", "impId1", ERROR_TIMED_OUT))),
                        entry("impId2", List.of(ImpRejection.of("bidder", "impId2", ERROR_TIMED_OUT))),
                        entry("impId3", List.of(ImpRejection.of("bidder", "impId3", ERROR_TIMED_OUT))));
    }

    @Test
    public void rejectBidsShouldTryRejectingEachBid() {
        // given
        target = new BidRejectionTracker("bidder", Set.of("impId1", "impId2", "impId3"), 0);
        final BidderBid bid0 = givenBid("bidId0", "impId1");
        target.reject(BidRejection.of(bid0, RESPONSE_REJECTED_GENERAL));

        // when
        final BidderBid bid1 = givenBid("bidId1", "impId1");
        final BidderBid bid2 = givenBid("bidId2", "impId2");
        final BidderBid bid3 = givenBid("bidId3", "impId3");
        target.reject(Set.of(
                BidRejection.of(bid1, RESPONSE_REJECTED_DSA_PRIVACY),
                BidRejection.of(bid2, RESPONSE_REJECTED_DSA_PRIVACY),
                BidRejection.of(bid3, RESPONSE_REJECTED_DSA_PRIVACY)));

        // then
        assertThat(target.getRejected()).extracting(Rejection::seat, Rejection::impId, Rejection::reason)
                .containsOnly(
                        tuple("seat", "impId1", RESPONSE_REJECTED_GENERAL),
                        tuple("seat", "impId2", RESPONSE_REJECTED_DSA_PRIVACY),
                        tuple("seat", "impId3", RESPONSE_REJECTED_DSA_PRIVACY));

        assertThat(target.getAllRejected())
                .containsOnly(
                        entry("impId1", List.of(
                                BidRejection.of(bid0, RESPONSE_REJECTED_GENERAL),
                                BidRejection.of(bid1, RESPONSE_REJECTED_DSA_PRIVACY))),
                        entry("impId2", List.of(BidRejection.of(bid2, RESPONSE_REJECTED_DSA_PRIVACY))),
                        entry("impId3", List.of(BidRejection.of(bid3, RESPONSE_REJECTED_DSA_PRIVACY))));
    }

    @Test
    public void getRejectedImpsShouldTreatUnsuccessfulImpsAsNoBidRejection() {
        // given
        target = new BidRejectionTracker("bidder", Set.of("impId1", "impId2"), 0);
        final BidderBid bid = BidderBid.builder().bid(Bid.builder().id("bidId1").impid("impId2").build()).build();
        target.succeed(singleton(bid));

        // then
        assertThat(target.getRejected()).extracting(Rejection::seat, Rejection::impId, Rejection::reason)
                .containsOnly(tuple("bidder", "impId1", NO_BID));
    }

    private BidderBid givenBid(String bidId, String impId) {
        return BidderBid.builder()
                .seat("seat")
                .bid(Bid.builder().id(bidId).impid(impId).build())
                .build();
    }
}
