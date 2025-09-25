package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class OptableTargetingConfig {

    String apiEndpoint
    String apiKey
    String tenant
    String origin
    Map<IdentifierType, String> ppidMapping
    Boolean adserverTargeting
    Long timeout
    String idPrefixOrder
    CacheProperties cache

    static OptableTargetingConfig getDefault(Map<IdentifierType, String> ppidMapping) {
        new OptableTargetingConfig().tap {
            it.apiEndpoint = PBSUtils.randomString
            it.adserverTargeting = true
            it.ppidMapping = ppidMapping
            it.cache = CacheProperties.default
        }
    }
}
