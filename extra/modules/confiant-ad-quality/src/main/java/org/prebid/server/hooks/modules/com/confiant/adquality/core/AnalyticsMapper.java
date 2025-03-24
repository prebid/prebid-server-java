package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import com.iab.openrtb.response.Bid;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.execution.v1.analytics.ActivityImpl;
import org.prebid.server.hooks.execution.v1.analytics.AppliedToImpl;
import org.prebid.server.hooks.execution.v1.analytics.ResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.v1.analytics.AppliedTo;
import org.prebid.server.hooks.v1.analytics.Result;
import org.prebid.server.hooks.v1.analytics.Tags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class AnalyticsMapper {

    private static final String AD_QUALITY_SCAN = "ad-scan";
    private static final String SUCCESS_STATUS = "success";
    private static final String SKIPPED = "skipped";
    private static final String INSPECTED_HAS_ISSUE = "inspected-has-issue";
    private static final String INSPECTED_NO_ISSUES = "inspected-no-issues";

    public static Tags toAnalyticsTags(List<BidderResponse> bidderResponsesWithIssues,
                                       List<BidderResponse> bidderResponsesWithoutIssues,
                                       List<BidderResponse> bidderResponsesNotScanned) {

        return TagsImpl.of(Collections.singletonList(ActivityImpl.of(
                AD_QUALITY_SCAN,
                SUCCESS_STATUS,
                toActivityResults(bidderResponsesWithIssues, bidderResponsesWithoutIssues, bidderResponsesNotScanned))));
    }

    private static List<Result> toActivityResults(List<BidderResponse> bidderResponsesWithIssues,
                                                  List<BidderResponse> bidderResponsesWithoutIssues,
                                                  List<BidderResponse> bidderResponsesNotScanned) {

        final List<Result> results = new ArrayList<>();
        if (!bidderResponsesNotScanned.isEmpty()) {
            results.add(ResultImpl.of(SKIPPED, null, toAppliedTo(bidderResponsesNotScanned)));
        }
        if (!bidderResponsesWithIssues.isEmpty()) {
            results.add(ResultImpl.of(INSPECTED_HAS_ISSUE, null, toAppliedTo(bidderResponsesWithIssues)));
        }
        if (!bidderResponsesWithoutIssues.isEmpty()) {
            results.add(ResultImpl.of(INSPECTED_NO_ISSUES, null, toAppliedTo(bidderResponsesWithoutIssues)));
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
        return bidderResponses.stream()
                .map(BidderResponse::getSeatBid)
                .flatMap(seatBid -> seatBid.getBids().stream())
                .map(BidderBid::getBid)
                .collect(Collectors.toList());
    }

    private AnalyticsMapper() {
    }
}
