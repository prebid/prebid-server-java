package org.prebid.server.functional.model.config

import org.prebid.server.functional.util.PBSUtils

class RuleEngineFunctionArgs {

    List<Object> countries
    List<Object> datacenters
    List<Object> sources
    List<Object> sids
    Object ptc
    String key
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
            ptc = PBSUtils.randomString
            key = PBSUtils.randomString
            domains = [PBSUtils.randomString]
            bundles = [PBSUtils.randomString]
            codes = [PBSUtils.randomString]
            types = [PBSUtils.randomString]
        }
    }
}
