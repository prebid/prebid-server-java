package org.prebid.server.settings.model.activity.rule;

public sealed interface AccountActivityRuleConfig permits
        AccountActivityComponentRuleConfig,
        AccountActivityGeoRuleConfig {
}
