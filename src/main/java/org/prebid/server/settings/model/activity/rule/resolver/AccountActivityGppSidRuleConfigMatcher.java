package org.prebid.server.settings.model.activity.rule.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import org.prebid.server.settings.model.activity.rule.AccountActivityGppSidRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

public class AccountActivityGppSidRuleConfigMatcher implements AccountActivityRuleConfigMatcher {

    @Override
    public boolean matches(JsonNode ruleNode) {
        return ruleNode != null && ruleNode.isObject() && ruleNode.has("gppSid");
    }

    @Override
    public Class<? extends AccountActivityRuleConfig> type() {
        return AccountActivityGppSidRuleConfig.class;
    }
}
