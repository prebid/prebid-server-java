package org.rtb.vexing.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.BidderStatus;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public final class BidderResult {

    BidderStatus bidderStatus;

    List<Bid> bids;

    boolean timedOut;
}
