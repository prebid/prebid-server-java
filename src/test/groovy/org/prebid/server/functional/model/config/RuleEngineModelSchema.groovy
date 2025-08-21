package org.prebid.server.functional.model.config

import groovy.transform.ToString

import static org.prebid.server.functional.model.config.RuleEngineFunction.DEVICE_COUNTRY_IN
import static org.prebid.server.functional.model.pricefloors.Country.USA

@ToString(includeNames = true, ignoreNulls = true)
class RuleEngineModelSchema {

    RuleEngineFunction function
    RuleEngineFunctionArgs args

    static RuleEngineModelSchema createDeviceCountryInSchema(List<Object> argsCountries = [USA]) {
        new RuleEngineModelSchema().tap {
            it.function = DEVICE_COUNTRY_IN
            it.args = new RuleEngineFunctionArgs(countries: argsCountries)
        }
    }
}
