package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import groovy.transform.ToString

@ToString(includeNames = true, ignoreNulls = true)
class AccountCoopSyncConfig {

    @JsonProperty("default")
    Boolean enabled
}
