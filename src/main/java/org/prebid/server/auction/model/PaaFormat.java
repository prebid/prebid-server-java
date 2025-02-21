package org.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum PaaFormat {

    @JsonProperty("original")
    ORIGINAL,

    @JsonProperty("iab")
    IAB
}
