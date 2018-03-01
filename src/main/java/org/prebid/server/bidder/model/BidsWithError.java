package org.prebid.server.bidder.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.proto.response.Bid;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class BidsWithError {

    List<Bid> bids;

    String error;

    boolean timedOut;
}
