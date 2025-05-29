package org.prebid.server.hooks.modules.rule.engine.core.rules;

import org.prebid.server.hooks.modules.rule.engine.core.rules.schema.Schema;
import org.prebid.server.hooks.modules.rule.engine.core.rules.tree.RuleTree;

public interface MatchingRuleFactory<SCHEMA_PAYLOAD, RULE_PAYLOAD, CONTEXT> {

    Rule<RULE_PAYLOAD, CONTEXT> create(Schema<SCHEMA_PAYLOAD> schema,
                                       RuleTree<RuleConfig<RULE_PAYLOAD, CONTEXT>> ruleTree,
                                       String analyticsKey,
                                       String modelVersion);
}
