package org.prebid.server.hooks.modules.ortb2.blocking.core;

import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.modules.ortb2.blocking.core.model.BlockedBids;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ResponseUpdater {

    private final BlockedBids blockedBids;

    private ResponseUpdater(BlockedBids blockedBids) {
        this.blockedBids = blockedBids;
    }

    public static ResponseUpdater create(BlockedBids blockedBids) {
        return new ResponseUpdater(Objects.requireNonNull(blockedBids));
    }

    public List<BidderBid> update(List<BidderBid> bids) {
        return IntStream.range(0, bids.size())
            .filter(index -> !blockedBids.getIndexes().contains(index))
            .mapToObj(bids::get)
            .collect(Collectors.toList());
    }
}
