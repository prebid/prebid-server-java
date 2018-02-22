package org.prebid.server.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.model.response.Bid;
import org.prebid.server.model.response.BidderStatus;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public final class BidderResult {

    BidderStatus bidderStatus;

    List<Bid> bids;

    boolean timedOut;
}
