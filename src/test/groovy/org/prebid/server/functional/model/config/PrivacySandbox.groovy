package org.prebid.server.functional.model.config

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.util.PBSUtils

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.LowerCaseStrategy)
class PrivacySandbox {

    CookieDeprecation cookieDeprecation

    static PrivacySandbox getDefaultPrivacySandbox(Boolean enabled = true, Integer ttlSeconds = PBSUtils.randomNumber) {
        new PrivacySandbox().tap {
            cookieDeprecation = CookieDeprecation.getDefaultCookieDeprecation(enabled, ttlSeconds)
        }
    }
}
