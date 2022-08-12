package org.prebid.server.hooks.execution.v1.bidder;

import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.hooks.v1.bidder.AllBidderResponsesPayload;

import java.util.List;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class AllBidderResponsesPayloadImpl implements AllBidderResponsesPayload {

    List<BidderResponse> bidderResponses;
}
