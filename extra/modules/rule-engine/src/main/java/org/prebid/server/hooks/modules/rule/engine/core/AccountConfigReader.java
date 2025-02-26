package org.prebid.server.hooks.modules.rule.engine.core;

import com.iab.openrtb.request.Imp;
import org.prebid.server.hooks.modules.rule.engine.core.config.model.AccountConfig;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleResult;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;

public class AccountConfigReader {

    public Rule<Imp, RequestRuleResult, RequestRuleContext> parse(AccountConfig config) {

    }
}
