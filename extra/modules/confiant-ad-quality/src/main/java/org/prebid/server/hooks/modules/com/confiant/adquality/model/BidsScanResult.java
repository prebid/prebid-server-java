package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import org.prebid.server.auction.model.BidderResponse;

import java.util.HashMap;
import java.util.List;
import java.util.stream.IntStream;

public class BidsScanResult {

    private final HashMap<Integer, BidScanResult> scanResultsMap = new HashMap<>();

    public void setScanResults(BidScanResult[][][] scanResults) {
        for (int i = 0; i < scanResults.length; i++) {
            scanResultsMap.put(i, scanResults[i][0][0]);
        }
    }

    public boolean hasIssues() {
        return !scanResultsMap.isEmpty() && scanResultsMap.entrySet().stream()
                .anyMatch(result -> result.getValue().getIssues().size() > 0);
    }

    public List<BidderResponse> filterValidResponses(List<BidderResponse> responses) {
        return IntStream.range(0, responses.size())
                .filter(ind -> !hasIssuesByBidIndex(ind))
                .mapToObj(responses::get)
                .toList();
    }

    private boolean hasIssuesByBidIndex(Integer ind) {
        final BidScanResult result = scanResultsMap.get(ind);
        return result != null && result.getIssues().size() > 0;
    }
}
