package org.prebid.server.activity;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ComponentType {

    BIDDER,

    ANALYTICS,

    @JsonProperty("rtd")
    RTD_MODULE,

    @JsonProperty("general")
    GENERAL_MODULE
}
