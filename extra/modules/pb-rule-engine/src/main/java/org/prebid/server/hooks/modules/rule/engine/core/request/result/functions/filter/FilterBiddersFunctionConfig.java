package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions.filter;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.prebid.server.auction.model.BidRejectionReason;

import java.util.Set;

@Value
@Builder
@Jacksonized
public class FilterBiddersFunctionConfig {

    Set<String> bidders;

    @Builder.Default
    @JsonProperty("seatnonbid")
    BidRejectionReason seatNonBid = BidRejectionReason.REQUEST_BLOCKED_OPTIMIZED;

    @JsonProperty("ifSyncedId")
    Boolean ifSyncedId;

    @JsonProperty("analyticsValue")
    String analyticsValue;
}
