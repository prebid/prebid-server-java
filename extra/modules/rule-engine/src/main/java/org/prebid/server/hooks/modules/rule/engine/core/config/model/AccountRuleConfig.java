package org.prebid.server.hooks.modules.rule.engine.core.config.model;

import lombok.Value;

import java.util.List;

@Value(staticConstructor = "of")
public class AccountRuleConfig {

    List<String> conditions;

    List<RuleFunctionConfig> results;
}
