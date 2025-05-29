package org.prebid.server.hooks.modules.rule.engine.core.rules;

import lombok.Value;
import org.prebid.server.hooks.modules.rule.engine.core.rules.result.RuleAction;

import java.util.List;

@Value(staticConstructor = "of")
public class RuleConfig<T, C> {

    String condition;

    List<RuleAction<T, C>> actions;
}
