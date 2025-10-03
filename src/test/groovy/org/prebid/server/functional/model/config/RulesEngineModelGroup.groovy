package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.config.RuleEngineModelRule.createRuleEngineModelRule
import static org.prebid.server.functional.model.config.RuleEngineModelSchema.createDeviceCountryInSchema

class RulesEngineModelGroup {

    Integer weight
    String version
    String analyticsKey
    List<RuleEngineModelSchema> schema
    @JsonProperty("default")
    List<RuleEngineModelDefault> modelDefault
    List<RuleEngineModelRule> rules

    static RulesEngineModelGroup createRulesModuleGroup() {
        new RulesEngineModelGroup().tap {
            it.weight = PBSUtils.getRandomNumber(1, 100)
            it.version = PBSUtils.randomString
            it.analyticsKey = PBSUtils.randomString
            it.schema = [createDeviceCountryInSchema()]
            it.modelDefault = []
            it.rules = [createRuleEngineModelRule()]
        }
    }
}
