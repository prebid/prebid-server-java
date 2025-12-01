package org.prebid.server.functional.model.config

import static java.lang.Boolean.TRUE
import static org.prebid.server.functional.model.config.RuleEngineModelRuleResult.createRuleEngineModelRuleWithExcludeResult

class RuleEngineModelRule {

    List<String> conditions
    List<RuleEngineModelRuleResult> results

    static RuleEngineModelRule createRuleEngineModelRule() {
        new RuleEngineModelRule().tap {
            it.conditions = [TRUE as String]
            it.results = [createRuleEngineModelRuleWithExcludeResult()]
        }
    }
}
