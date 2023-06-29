package org.prebid.server.settings.model.activity.rule.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import org.prebid.server.settings.model.activity.rule.AccountActivityGeoRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

public class AccountActivityGeoRuleConfigMatcher implements AccountActivityRuleConfigMatcher {

    @Override
    public boolean matches(JsonNode ruleNode) {
        if (ruleNode == null || !ruleNode.isObject()) {
            return false;
        }

        final JsonNode conditionNode = ruleNode.get("condition");
        return conditionNode != null && conditionNode.isObject()
                && (conditionNode.has("gppSid") || conditionNode.has("geo"));
    }

    @Override
    public Class<? extends AccountActivityRuleConfig> type() {
        return AccountActivityGeoRuleConfig.class;
    }
}
