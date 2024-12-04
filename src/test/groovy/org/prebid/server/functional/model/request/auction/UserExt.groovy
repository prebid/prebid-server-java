package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy)
class UserExt {

    String consent
    List<Eid> eids
    List<String> fcapids
    UserTime time
    UserExtData data
    UserExtPrebid prebid
    ConsentedProvidersSettings consentedProvidersSettings
    @JsonProperty("ConsentedProvidersSettings")
    ConsentedProvidersSettings consentedProvidersSettingsCamelCase

    static UserExt getFPDUserExt() {
        new UserExt(data: UserExtData.FPDUserExtData)
    }
}
