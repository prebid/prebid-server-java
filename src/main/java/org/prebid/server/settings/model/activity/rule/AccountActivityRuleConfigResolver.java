package org.prebid.server.settings.model.activity.rule;

import com.fasterxml.jackson.databind.JsonNode;

public class AccountActivityRuleConfigResolver {

    private AccountActivityRuleConfigResolver() {
    }

    public static Class<? extends AccountActivityRuleConfig> resolve(JsonNode ruleNode) {
        return AccountActivityComponentRuleConfig.class;
    }
}
