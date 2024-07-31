package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class CookieDeprecation {

    Boolean enabled
    @JsonProperty("ttlsec")
    Integer ttlSeconds

    static CookieDeprecation getDefaultCookieDeprecation(Boolean enabled = true, Integer ttlSeconds = PBSUtils.randomNumber) {
        new CookieDeprecation().tap {
            it.enabled = enabled
            it.ttlSeconds = ttlSeconds
        }
    }
}
