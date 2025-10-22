package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class CacheProperties {

    Boolean enabled
    Integer ttlSeconds

    static CacheProperties getDefault() {
        new CacheProperties().tap {
            enabled = true
            ttlSeconds = PBSUtils.randomNumber
        }
    }
}
