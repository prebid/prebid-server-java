package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.iab.openrtb.request.BidRequest;
import lombok.Value;
import org.prebid.server.hooks.v1.analytics.Tags;
import org.prebid.server.model.UpdateResult;

@Value(staticConstructor = "of")
public class RequestRuleResult {

    UpdateResult<BidRequest> bidRequest;

    Tags analyticsTags;
}
