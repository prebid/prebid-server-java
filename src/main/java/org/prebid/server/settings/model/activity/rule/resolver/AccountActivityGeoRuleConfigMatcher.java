package org.prebid.server.settings.model.activity.rule.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import org.prebid.server.settings.model.activity.rule.AccountActivityGeoRuleConfig;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

public class AccountActivityGeoRuleConfigMatcher implements AccountActivityRuleConfigMatcher {

    @Override
    public boolean matches(JsonNode ruleNode) {
        return ruleNode != null && ruleNode.isObject()
                && (ruleNode.has("gppSid") || ruleNode.has("geo"));
    }

    @Override
    public Class<? extends AccountActivityRuleConfig> type() {
        return AccountActivityGeoRuleConfig.class;
    }
}
