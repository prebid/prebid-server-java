package org.prebid.server.bidder.optout.model;

import java.util.List;

public record OptoutResponse(List<OptoutBid> bids) {
}
