package org.prebid.server.hooks.modules.rule.engine.core.rules;

import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.modules.rule.engine.core.request.context.RequestResultContext;

@Builder
@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class PerStageRule {

    Rule<BidRequest, RequestResultContext> processedAuctionRequestRule;
}

