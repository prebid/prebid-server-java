package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum AccountAuctionBidRoundingMode {

    @JsonProperty("down")
    @JsonEnumDefaultValue
    DOWN,

    @JsonProperty("true")
    TRUE,

    @JsonProperty("timesplit")
    TIMESPLIT,

    @JsonProperty("up")
    UP
}
