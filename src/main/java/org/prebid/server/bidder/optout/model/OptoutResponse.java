package org.prebid.server.bidder.optout.model;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class OptoutResponse {

    List<OptoutBid> bids;
}
