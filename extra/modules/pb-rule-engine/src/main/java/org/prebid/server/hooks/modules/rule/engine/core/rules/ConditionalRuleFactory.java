package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.Schema;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTree;

public interface ConditionalRuleFactory<T, C> {

    Rule<T, C> create(Schema<T, C> schema,
                      RuleTree<RuleConfig<T, C>> ruleTree,
                      String analyticsKey,
                      String modelVersion);
}
