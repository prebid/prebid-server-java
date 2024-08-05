package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

@JsonNaming(PropertyNamingStrategies.KebabCaseStrategy)
class AccountMetricsConfig {

    AccountMetricsVerbosityLevel verbosityLevel
    @JsonProperty("verbosity_level")
    AccountMetricsVerbosityLevel verbosityLevelSnakeCase
}
