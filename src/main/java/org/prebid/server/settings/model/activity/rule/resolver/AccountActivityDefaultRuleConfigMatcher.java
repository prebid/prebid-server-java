package org.prebid.server.settings.model.activity.rule.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import org.prebid.server.settings.model.activity.rule.AccountActivityComponentRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

public class AccountActivityDefaultRuleConfigMatcher implements AccountActivityRuleConfigMatcher {

    @Override
    public boolean matches(JsonNode ruleNode) {
        return true;
    }

    @Override
    public Class<? extends AccountActivityRuleConfig> type() {
        return AccountActivityComponentRuleConfig.class;
    }
}
