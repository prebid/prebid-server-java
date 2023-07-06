package org.prebid.server.settings.model.activity.rule.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import org.prebid.server.settings.model.activity.rule.AccountActivityAppliedPrivacyModulesRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

public class AccountActivityAppliedPrivacyModulesRuleConfigMatcher implements AccountActivityRuleConfigMatcher {

    @Override
    public boolean matches(JsonNode ruleNode) {
        return isNotNullObjectNode(ruleNode) && ruleNode.has("privacyreg");
    }

    private static boolean isNotNullObjectNode(JsonNode jsonNode) {
        return jsonNode != null && jsonNode.isObject();
    }

    @Override
    public Class<? extends AccountActivityRuleConfig> type() {
        return AccountActivityAppliedPrivacyModulesRuleConfig.class;
    }
}
