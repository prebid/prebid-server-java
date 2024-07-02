package org.prebid.server.hooks.modules.pb.richmedia.filter.core;

import com.iab.openrtb.response.Bid;
import org.junit.Test;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.hooks.modules.pb.richmedia.filter.model.AnalyticsResult;
import org.prebid.server.hooks.modules.pb.richmedia.filter.model.MraidFilterResult;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class BidResponsesMraidFilterTest {

    private final BidResponsesMraidFilter target = new BidResponsesMraidFilter();

    @Test
    public void filterShouldReturnOriginalBidsWhenNoBidsHaveMraidScriptInAdm() {
        // given
        final BidderResponse responseA = givenBidderResponse("bidderA", List.of(givenBid("imp_id", "adm1")));
        final BidderResponse responseB = givenBidderResponse("bidderB", List.of(givenBid("imp_id", "adm2")));
        final BidRejectionTracker bidRejectionTrackerA = mock(BidRejectionTracker.class);
        final BidRejectionTracker bidRejectionTrackerB = mock(BidRejectionTracker.class);
        final Map<String, BidRejectionTracker> givenTrackers = Map.of(
                "bidderA", bidRejectionTrackerA,
                "bidderB", bidRejectionTrackerB);

        // when
        final MraidFilterResult filterResult = target.filterByPattern("mraid.js", List.of(responseA, responseB), givenTrackers);

        // then
        assertThat(filterResult.getFilterResult()).containsExactly(responseA, responseB);
        assertThat(filterResult.getAnalyticsResult()).isEmpty();
        assertThat(filterResult.hasRejectedBids()).isFalse();

        verifyNoInteractions(bidRejectionTrackerA, bidRejectionTrackerB);
    }

    @Test
    public void filterShouldReturnFilteredBidsWhenBidsWithMraidScriptIsFilteredOut() {
        // given
        final BidderResponse responseA = givenBidderResponse("bidderA", List.of(
                givenBid("imp_id1", "adm1"),
                givenBid("imp_id2", "adm2")));
        final BidderResponse responseB = givenBidderResponse("bidderB", List.of(
                givenBid("imp_id1", "adm1"),
                givenBid("imp_id2", "adm2_mraid.js")));
        final BidderResponse responseC = givenBidderResponse("bidderC", List.of(
                givenBid("imp_id1", "adm1_mraid.js"),
                givenBid("imp_id2", "adm2_mraid.js")));

        final BidRejectionTracker bidRejectionTrackerA = mock(BidRejectionTracker.class);
        final BidRejectionTracker bidRejectionTrackerB = mock(BidRejectionTracker.class);
        final BidRejectionTracker bidRejectionTrackerC = mock(BidRejectionTracker.class);
        final Map<String, BidRejectionTracker> givenTrackers = Map.of(
                "bidderA", bidRejectionTrackerA,
                "bidderB", bidRejectionTrackerB,
                "bidderC", bidRejectionTrackerC);

        // when
        final MraidFilterResult filterResult = target.filterByPattern(
                "mraid.js",
                List.of(responseA, responseB, responseC),
                givenTrackers);

        // then
        final BidderResponse expectedResponseA = givenBidderResponse(
                "bidderA",
                List.of(givenBid("imp_id1", "adm1"), givenBid("imp_id2", "adm2")));
        final BidderResponse expectedResponseB = givenBidderResponse(
                "bidderB",
                List.of(givenBid("imp_id1", "adm1")),
                List.of(givenError("imp_id2")));
        final BidderResponse expectedResponseC = givenBidderResponse(
                "bidderC",
                List.of(),
                List.of(givenError("imp_id1", "imp_id2")));

        final AnalyticsResult expectedAnalyticsResultB = AnalyticsResult.of(
                "success-block",
                Map.of("richmedia-format", "mraid"),
                "bidderB",
                List.of("imp_id2"));
        final AnalyticsResult expectedAnalyticsResultC = AnalyticsResult.of(
                "success-block",
                Map.of("richmedia-format", "mraid"),
                "bidderC",
                List.of("imp_id1", "imp_id2"));

        assertThat(filterResult.getFilterResult())
                .containsExactly(expectedResponseA, expectedResponseB, expectedResponseC);
        assertThat(filterResult.getAnalyticsResult())
                .containsExactlyInAnyOrder(expectedAnalyticsResultB, expectedAnalyticsResultC);
        assertThat(filterResult.hasRejectedBids()).isTrue();

        verifyNoInteractions(bidRejectionTrackerA);
        verify(bidRejectionTrackerB)
                .reject(List.of("imp_id2"), BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
        verify(bidRejectionTrackerC)
                .reject(List.of("imp_id1", "imp_id2"), BidRejectionReason.RESPONSE_REJECTED_INVALID_CREATIVE);
        verifyNoMoreInteractions(bidRejectionTrackerB, bidRejectionTrackerC);
    }

    private static BidderResponse givenBidderResponse(String bidder, List<BidderBid> bids) {
        return BidderResponse.of(bidder, BidderSeatBid.of(bids), 100);
    }

    private static BidderResponse givenBidderResponse(String bidder, List<BidderBid> bids, List<BidderError> errors) {
        return BidderResponse.of(bidder, BidderSeatBid.empty().with(bids, errors), 100);
    }

    private static BidderBid givenBid(String impId, String adm) {
        return BidderBid.builder().bid(Bid.builder().impid(impId).adm(adm).build()).build();
    }

    private static BidderError givenError(String... rejectedImps) {
        return BidderError.of("Invalid bid", BidderError.Type.invalid_bid, Set.of(rejectedImps));
    }

}
