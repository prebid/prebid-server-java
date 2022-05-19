package org.prebid.server.bidder.stroeercore.model;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class StroeerCoreBidResponse {

    List<StroeerCoreBid> bids;
}
