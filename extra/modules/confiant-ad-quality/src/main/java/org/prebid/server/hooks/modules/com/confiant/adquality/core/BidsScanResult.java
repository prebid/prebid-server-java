package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.OperationResult;

import java.util.List;
import java.util.stream.IntStream;

public class BidsScanResult {

    private final OperationResult<List<BidScanResult>> result;

    public BidsScanResult(OperationResult<List<BidScanResult>> result) {
        this.result = result;
    }

    public boolean hasIssues() {
        return !result.getValue().isEmpty() && result.getValue().stream()
                .anyMatch(result -> result.getIssues() != null && result.getIssues().size() > 0);
    }

    public List<BidderResponse> filterValidResponses(List<BidderResponse> responses) {
        return IntStream.range(0, responses.size())
                .filter(ind -> !hasIssuesByBidIndex(ind))
                .mapToObj(responses::get)
                .toList();
    }

    public List<String> getIssuesMessages() {
        return result.getValue().stream()
                .map(r -> r.getTagKey() + ": " + r.getIssues().toString())
                .toList();
    }
    public List<String> getDebugMessages() {
        return result.getDebugMessages();
    }

    private boolean hasIssuesByBidIndex(Integer ind) {
        final BidScanResult bidResult = result.getValue().get(ind);
        return bidResult != null && bidResult.getIssues() != null && bidResult.getIssues().size() > 0;
    }
}
