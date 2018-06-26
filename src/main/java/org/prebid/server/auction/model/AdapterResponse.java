package org.prebid.server.auction.model;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.BidderStatus;

import java.util.List;

@AllArgsConstructor(staticName = "of")
@Value
public class AdapterResponse {

    BidderStatus bidderStatus;

    List<Bid> bids;

    BidderError error;
}
