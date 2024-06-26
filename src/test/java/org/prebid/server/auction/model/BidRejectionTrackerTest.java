package org.prebid.server.auction.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

public class BidRejectionTrackerTest {

    private BidRejectionTracker target;

    @BeforeEach
    public void setUp() {
        target = new BidRejectionTracker("bidder", singleton("1"), 0);
    }

    @Test
    public void succeedShouldRestoreBidderFromRejection() {
        // given
        target.reject("1", BidRejectionReason.OTHER_ERROR);

        // when
        target.succeed("1");

        // then
        assertThat(target.getRejectionReasons()).isEmpty();
    }

    @Test
    public void succeedShouldIgnoreUninvolvedImpIds() {
        // given
        target.reject("1", BidRejectionReason.OTHER_ERROR);

        // when
        target.succeed("2");

        // then
        assertThat(target.getRejectionReasons())
                .isEqualTo(singletonMap("1", BidRejectionReason.OTHER_ERROR));
    }

    @Test
    public void rejectShouldRecordRejectionFirstTimeIfImpIdIsInvolved() {
        // when
        target.reject("1", BidRejectionReason.OTHER_ERROR);

        // then
        assertThat(target.getRejectionReasons())
                .isEqualTo(singletonMap("1", BidRejectionReason.OTHER_ERROR));
    }

    @Test
    public void rejectShouldNotRecordRejectionIfImpIdIsNotInvolved() {
        // when
        target.reject("2", BidRejectionReason.OTHER_ERROR);

        // then
        assertThat(target.getRejectionReasons()).doesNotContainKey("2");
    }

    @Test
    public void rejectShouldNotRecordRejectionIfImpIdIsAlreadyRejected() {
        // given
        target.reject("1", BidRejectionReason.OTHER_ERROR);

        // when
        target.reject("1", BidRejectionReason.FAILED_TO_REQUEST_BIDS);

        // then
        assertThat(target.getRejectionReasons())
                .isEqualTo(singletonMap("1", BidRejectionReason.OTHER_ERROR));
    }

    @Test
    public void rejectAllShouldTryRejectingEachImpId() {
        // given
        target = new BidRejectionTracker("bidder", Set.of("1", "2", "3"), 0);
        target.reject("1", BidRejectionReason.NO_BID);

        // when
        target.rejectAll(BidRejectionReason.TIMED_OUT);

        // then
        assertThat(target.getRejectionReasons())
                .isEqualTo(Map.of(
                        "1", BidRejectionReason.NO_BID,
                        "2", BidRejectionReason.TIMED_OUT,
                        "3", BidRejectionReason.TIMED_OUT));
    }

    @Test
    public void getRejectionReasonsShouldTreatUnsuccessfulBidsAsNoBidRejection() {
        // given
        target = new BidRejectionTracker("bidder", Set.of("1", "2"), 0);
        target.succeed("2");

        // then
        assertThat(target.getRejectionReasons()).isEqualTo(singletonMap("1", BidRejectionReason.NO_BID));
    }
}
