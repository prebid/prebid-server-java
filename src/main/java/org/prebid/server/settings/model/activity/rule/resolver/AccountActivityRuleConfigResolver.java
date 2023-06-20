package org.prebid.server.settings.model.activity.rule.resolver;

import com.fasterxml.jackson.databind.JsonNode;
import org.prebid.server.settings.model.activity.rule.AccountActivityRuleConfig;

import java.util.List;

public class AccountActivityRuleConfigResolver {

    private AccountActivityRuleConfigResolver() {
    }

    private static final List<AccountActivityRuleConfigMatcher> MATCHERS = List.of(
            new AccountActivityGeoRuleConfigMatcher(),
            new AccountActivityDefaultRuleConfigMatcher());

    public static Class<? extends AccountActivityRuleConfig> resolve(JsonNode ruleNode) {
        return MATCHERS.stream()
                .filter(matcher -> matcher.matches(ruleNode))
                .findFirst()
                .orElseGet(() -> MATCHERS.get(MATCHERS.size() - 1))
                .type();
    }
}
