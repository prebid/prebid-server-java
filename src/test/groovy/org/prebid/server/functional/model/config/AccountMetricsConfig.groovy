package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonProperty


class AccountMetricsConfig {

    @JsonProperty("verbosity-level")
    AccountMetricsVerbosityLevel verbosityLevel;
}
