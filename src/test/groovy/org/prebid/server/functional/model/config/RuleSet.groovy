package org.prebid.server.functional.model.config

import static org.prebid.server.functional.model.config.Stage.PROCESSED_AUCTION_REQUEST
import static org.prebid.server.functional.util.PBSUtils.randomString

class RuleSet {

    Boolean enabled
    Stage stage
    String name
    String version
    List<RulesEngineModelGroups> modelGroups

    static RuleSet createRuleSets() {
        new RuleSet().tap {
            it.enabled = true
            it.stage = PROCESSED_AUCTION_REQUEST
            it.name = randomString
            it.version = randomString
            it.modelGroups = [RulesEngineModelGroups.createRulesModuleGroup()]
        }
    }
}
