package org.prebid.server.functional.model.request.auction

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class UserExt {

    String consent
    List<Eid> eids
    List<String> fcapids
    UserTime time
    UserExtData data
    UserExtPrebid prebid
    @JsonProperty("consented_providers_settings")
    ConsentedProvidersSettings consentedProvidersSettings

    static UserExt getFPDUserExt() {
        new UserExt(data: UserExtData.FPDUserExtData)
    }
}
