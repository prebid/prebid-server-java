package org.prebid.server.hooks.execution.v1.bidder;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.hooks.v1.bidder.BidderResponsePayload;

import java.util.List;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class BidderResponsePayloadImpl implements BidderResponsePayload {

    List<BidderBid> bids;
}
