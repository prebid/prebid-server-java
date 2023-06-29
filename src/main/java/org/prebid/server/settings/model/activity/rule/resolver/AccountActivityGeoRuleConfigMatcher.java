package org.prebid.server.settings.model.activity.rule.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import org.prebid.server.settings.model.activity.rule.AccountActivityGeoRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

public class AccountActivityGeoRuleConfigMatcher implements AccountActivityRuleConfigMatcher {

    @Override
    public boolean matches(JsonNode ruleNode) {
        final JsonNode conditionNode = isNotNullObjectNode(ruleNode) ? ruleNode.get("condition") : null;
        return isNotNullObjectNode(conditionNode)
                && (conditionNode.has("gppSid")
                || conditionNode.has("geo")
                || conditionNode.has("gpc"));
    }

    private static boolean isNotNullObjectNode(JsonNode jsonNode) {
        return jsonNode != null && jsonNode.isObject();
    }

    @Override
    public Class<? extends AccountActivityRuleConfig> type() {
        return AccountActivityGeoRuleConfig.class;
    }
}
