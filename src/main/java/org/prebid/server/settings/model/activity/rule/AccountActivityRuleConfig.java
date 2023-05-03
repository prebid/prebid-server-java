package org.prebid.server.settings.model.activity.rule;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION, include = JsonTypeInfo.As.EXISTING_PROPERTY)
@JsonSubTypes({@JsonSubTypes.Type(value = AccountActivityComponentRuleConfig.class)})
public sealed interface AccountActivityRuleConfig permits AccountActivityComponentRuleConfig {
}
