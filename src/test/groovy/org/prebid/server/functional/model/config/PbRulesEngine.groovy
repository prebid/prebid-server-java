package org.prebid.server.functional.model.config

class PbRulesEngine {

    Boolean enabled
    Boolean generateRulesFromBidderConfig
    List<RuleSets> ruleSets

    static PbRulesEngine createRulesEngineWithRule(Boolean enabled = true) {
        new PbRulesEngine().tap {
            it.enabled = enabled
            it.generateRulesFromBidderConfig = false
            it.ruleSets = [RuleSets.createRuleSets()]
        }
    }
}
