package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import org.prebid.server.functional.util.PBSUtils

class RuleEngineFunctionArgs {

    List<Object> countries
    List<Object> datacenters
    List<Object> sources
    List<Object> sids
    @JsonProperty("pct")
    Object percent
    Object key
    List<Object> domains
    List<Object> bundles
    List<Object> codes
    List<Object> types
    String operator
    BigDecimal value
    Currency currency

    static RuleEngineFunctionArgs getDefaultFunctionArgs() {
        new RuleEngineFunctionArgs().tap {
            countries = [PBSUtils.randomString]
            datacenters = [PBSUtils.randomString]
            sources = [PBSUtils.randomString]
            sids = [PBSUtils.randomNumber]
            percent = PBSUtils.getRandomNumber(1, 100)
            key = PBSUtils.randomString
            domains = [PBSUtils.randomString]
            bundles = [PBSUtils.randomString]
            codes = [PBSUtils.randomString]
            types = [PBSUtils.randomString]
        }
    }
}
