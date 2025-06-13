package org.prebid.server.hooks.modules.rule.engine.core.request.result.functions;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value(staticConstructor = "of")
public class FilterBiddersFunctionConfig {

    List<String> bidders;

    @Builder.Default
    Integer seatNonBid = 203;

    Boolean ifSyncedId;

    String analyticsValue;
}
