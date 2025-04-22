package org.prebid.server.hooks.modules.rule.engine.core.rules.request;

import com.iab.openrtb.request.BidRequest;

public interface RequestRule {

    RequestRuleResult process(BidRequest bidRequest);
}
