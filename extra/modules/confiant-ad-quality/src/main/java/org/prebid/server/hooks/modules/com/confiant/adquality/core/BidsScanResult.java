package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.OperationResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class BidsScanResult {

    private final OperationResult<List<BidScanResult>> result;

    public BidsScanResult(OperationResult<List<BidScanResult>> result) {
        this.result = result;
    }

    public boolean hasIssues() {
        return Optional.ofNullable(result.getValue())
                .map(scanResults -> scanResults.stream()
                        .anyMatch(result -> Optional.ofNullable(result.getIssues())
                                .map(issues -> !issues.isEmpty())
                                .orElse(false)))
                .orElse(false);
    }

    public Map<Boolean, List<BidderResponse>> toIssuesExistencyMap(List<BidderResponse> bidderResponses) {
        final List<BidderResponse> bidderResponsesWithIssues = new ArrayList<>();
        final List<BidderResponse> bidderResponsesWithoutIssues = new ArrayList<>();
        final int scanSize = result.getValue().size();

        for (int i = 0; i < scanSize; i++) {
            if (hasIssuesByBidIndex(i)) {
                bidderResponsesWithIssues.add(bidderResponses.get(i));
            } else {
                bidderResponsesWithoutIssues.add(bidderResponses.get(i));
            }
        }

        final Map<Boolean, List<BidderResponse>> issuesExistencyMap = new HashMap<>();
        issuesExistencyMap.put(true, bidderResponsesWithIssues);
        issuesExistencyMap.put(false, bidderResponsesWithoutIssues);

        return issuesExistencyMap;
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
        final BidScanResult bidResult = result.getValue().get(ind);
        return bidResult != null && bidResult.getIssues() != null && !bidResult.getIssues().isEmpty();
    }
}
