package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.GroupByIssues;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.OperationResult;

import java.util.ArrayList;
import java.util.List;

public class BidsScanResult {

    private final OperationResult<List<BidScanResult>> result;

    public BidsScanResult(OperationResult<List<BidScanResult>> result) {
        this.result = result;
    }

    public GroupByIssues<BidderResponse> toGroupByIssues(List<BidderResponse> bidderResponses) {
        final List<BidderResponse> bidderResponsesWithIssues = new ArrayList<>();
        final List<BidderResponse> bidderResponsesWithoutIssues = new ArrayList<>();
        final int groupSize = bidderResponses.size();

        for (int i = 0; i < groupSize; i++) {
            if (hasIssuesByBidIndex(i)) {
                bidderResponsesWithIssues.add(bidderResponses.get(i));
            } else {
                bidderResponsesWithoutIssues.add(bidderResponses.get(i));
            }
        }

        return GroupByIssues.<BidderResponse>builder()
                .withIssues(bidderResponsesWithIssues)
                .withoutIssues(bidderResponsesWithoutIssues)
                .build();
    }

    public List<String> getIssuesMessages() {
        return result.getValue().stream()
                .map(r -> r.getTagKey() + ": " + (r.getIssues() == null ? "no issues" : r.getIssues().toString()))
                .toList();
    }

    public List<String> getDebugMessages() {
        return result.getDebugMessages();
    }

    private boolean hasIssuesByBidIndex(Integer ind) {
        final List<BidScanResult> bidScanResults = result.getValue();
        final BidScanResult bidResult = bidScanResults.size() > ind ? bidScanResults.get(ind) : null;
        return bidResult != null && bidResult.getIssues() != null && !bidResult.getIssues().isEmpty();
    }
}
