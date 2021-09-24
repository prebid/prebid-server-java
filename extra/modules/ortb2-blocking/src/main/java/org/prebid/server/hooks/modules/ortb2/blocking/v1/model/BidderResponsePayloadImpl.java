package org.prebid.server.hooks.modules.ortb2.blocking.v1.model;

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
