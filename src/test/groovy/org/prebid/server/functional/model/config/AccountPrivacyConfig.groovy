package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString
import org.prebid.server.functional.model.request.auction.AllowActivities

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class AccountPrivacyConfig {

    AccountGdprConfig gdpr
    AccountCcpaConfig ccpa
    @JsonProperty("allowactivities")
    AllowActivities allowActivities
    List<AccountGppConfig> modules
}
