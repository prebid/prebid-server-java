package org.prebid.server.bidder.stroeercore.model;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class StroeerCoreBidResponse {

    List<StroeerCoreBid> bids;
}
