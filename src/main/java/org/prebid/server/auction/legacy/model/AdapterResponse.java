package org.prebid.server.auction.legacy.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.proto.response.legacy.Bid;
import org.prebid.server.proto.response.legacy.BidderStatus;

import java.util.List;

@Deprecated
@AllArgsConstructor(staticName = "of")
@Value
public class AdapterResponse {

    BidderStatus bidderStatus;

    List<Bid> bids;

    BidderError error;
}
