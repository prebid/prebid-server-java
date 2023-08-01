package org.prebid.server.settings.model.activity.rule.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

public interface AccountActivityRuleConfigMatcher {

    boolean matches(JsonNode ruleNode);

    Class<? extends AccountActivityRuleConfig> type();
}
