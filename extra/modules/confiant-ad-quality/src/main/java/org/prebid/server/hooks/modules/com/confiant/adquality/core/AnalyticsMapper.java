package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import com.iab.openrtb.response.Bid;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.analytics.ActivityImpl;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.analytics.AppliedToImpl;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.analytics.ResultImpl;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.analytics.TagsImpl;
import org.prebid.server.hooks.v1.analytics.AppliedTo;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AnalyticsMapper {

    private static final String AD_QUALITY_SCAN = "ad-quality-scan";

    private static final String SUCCESS_STATUS = "success";

    private static final String SKIPPED = "skipped";

    private static final String INSPECTED_HAS_ISSUE = "inspected-has-issue";

    private static final String INSPECTED_NO_ISSUES = "inspected-no-issues";

    public static Tags toAnalyticsTags(BidsScanResult bidsScanResult,
                                       List<BidderResponse> scannedBidderResponses,
                                       List<BidderResponse> notScannedBidderResponses) {
        return TagsImpl.of(Collections.singletonList(ActivityImpl.of(
                AD_QUALITY_SCAN,
                SUCCESS_STATUS,
                toActivityResults(bidsScanResult, scannedBidderResponses, notScannedBidderResponses))));
    }

    private static List<Result> toActivityResults(BidsScanResult bidsScanResult,
                                           List<BidderResponse> scannedBidderResponses,
                                           List<BidderResponse> notScannedBidderResponses) {
        final List<BidderResponse> hasIssues = new ArrayList<>();
        final List<BidderResponse> noIssues = new ArrayList<>();
        for (int i = 0; i < scannedBidderResponses.size(); i++) {
            if (bidsScanResult.hasIssuesByBidIndex(i)) {
                hasIssues.add(scannedBidderResponses.get(i));
            } else {
                noIssues.add(scannedBidderResponses.get(i));
            }
        }

        final List<Result> results = new ArrayList<>();
        if (!notScannedBidderResponses.isEmpty()) {
            results.add(ResultImpl.of(SKIPPED, null, toAppliedTo(notScannedBidderResponses)));
        }
        if (!hasIssues.isEmpty()) {
            results.add(ResultImpl.of(INSPECTED_HAS_ISSUE, null, toAppliedTo(hasIssues)));
        }
        if (!noIssues.isEmpty()) {
            results.add(ResultImpl.of(INSPECTED_NO_ISSUES, null, toAppliedTo(noIssues)));
        }

        return results;
    }

    private static AppliedTo toAppliedTo(List<BidderResponse> bidderResponses) {
        final List<Bid> bids = toBids(bidderResponses);
        return AppliedToImpl.builder()
                .bidders(bidderResponses.stream().map(BidderResponse::getBidder).toList())
                .impIds(bids.stream().map(Bid::getImpid).toList())
                .bidIds(bids.stream().map(Bid::getId).toList())
                .build();
    }

    private static List<Bid> toBids(List<BidderResponse> bidderResponses) {
        return bidderResponses.stream().map(bidderResponse -> bidderResponse.getSeatBid().getBids()
                        .stream().map(BidderBid::getBid).toList()).flatMap(List::stream)
                .collect(Collectors.toList());
    }
}
