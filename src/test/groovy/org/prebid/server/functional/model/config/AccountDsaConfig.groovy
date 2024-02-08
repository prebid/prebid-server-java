package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.Dsa

@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
@ToString(includeNames = true, ignoreNulls = true)
class AccountDsaConfig {

    @JsonProperty("default")
    Dsa defaultDsa

    Boolean gdprOnly
}
