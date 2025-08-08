package org.prebid.server.hooks.modules.rule.engine.core.rules;

import com.iab.openrtb.request.BidRequest;
import lombok.Builder;
import lombok.Value;
import lombok.experimental.Accessors;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;

import java.time.Instant;

@Builder
@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class PerStageRule {

    Instant timestamp;

    Rule<BidRequest, RequestRuleContext> processedAuctionRequestRule;
}

