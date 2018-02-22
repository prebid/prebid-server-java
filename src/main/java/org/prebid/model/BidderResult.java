package org.prebid.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.model.response.Bid;
import org.prebid.model.response.BidderStatus;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public final class BidderResult {

    BidderStatus bidderStatus;

    List<Bid> bids;

    boolean timedOut;
}
