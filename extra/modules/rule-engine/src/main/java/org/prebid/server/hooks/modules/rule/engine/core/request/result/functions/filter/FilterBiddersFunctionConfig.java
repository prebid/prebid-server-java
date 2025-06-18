package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.filter;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.auction.model.BidRejectionReason;

import java.util.Set;

@Builder
@Value(staticConstructor = "of")
public class FilterBiddersFunctionConfig {

    Set<String> bidders;

    @Builder.Default
    BidRejectionReason seatNonBid = 203;

    Boolean ifSyncedId;

    String analyticsValue;
}
