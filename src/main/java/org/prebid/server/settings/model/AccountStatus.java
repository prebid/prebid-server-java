package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum AccountStatus {

    @JsonProperty("active")
    ACTIVE,
    @JsonProperty("inactive")
    INACTIVE
}
