package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class AccountCookieSyncConfig {

    Integer defaultLimit
    Integer maxLimit
    List<String> pri
    AccountCoopSyncConfig coopSync

    @JsonProperty("default_limit")
    Integer defaultLimitSnakeCase
    @JsonProperty("max_limit")
    Integer maxLimitSnakeCase
    @JsonProperty("coop_sync")
    AccountCoopSyncConfig coopSyncSnakeCase
}
