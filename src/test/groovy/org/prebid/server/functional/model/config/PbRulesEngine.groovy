package org.prebid.server.functional.model.config

import java.time.ZonedDateTime

class PbRulesEngine {

    Boolean enabled
    Boolean generateRulesFromBidderConfig
    ZonedDateTime timestamp
    List<RuleSet> ruleSets

    static PbRulesEngine createRulesEngineWithRule(Boolean enabled = true) {
        new PbRulesEngine().tap {
            it.enabled = enabled
            it.generateRulesFromBidderConfig = false
            it.ruleSets = [RuleSet.createRuleSets()]
        }
    }
}
