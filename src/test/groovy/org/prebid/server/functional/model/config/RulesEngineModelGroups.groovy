package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import org.prebid.server.functional.model.pricefloors.Country
import org.prebid.server.functional.util.PBSUtils

import static org.prebid.server.functional.model.config.RuleEngineFunction.DEVICE_COUNTRY_IN
import static org.prebid.server.functional.model.config.RuleEngineModelRule.*
import static org.prebid.server.functional.model.config.RuleEngineModelSchema.createDeviceCountryInSchema

class RulesEngineModelGroups {

    Integer weight
    String version
    String analyticsKey
    List<RuleEngineModelSchema> schema
    @JsonProperty("default")
    List<RuleEngineModelDefault> modelDefault
    List<RuleEngineModelRule> rules

    static RulesEngineModelGroups createRulesModuleGroup() {
        new RulesEngineModelGroups().tap {
            it.weight = PBSUtils.getRandomNumber(0, 100)
            it.version = PBSUtils.randomString
            it.analyticsKey = PBSUtils.randomString
            it.schema = [createDeviceCountryInSchema()]
            it.modelDefault = []
            it.rules = [createRuleEngineModelRule()]
        }
    }
}
