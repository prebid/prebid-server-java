package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum TraceLevel {

    @JsonProperty("basic")
    BASIC,
    @JsonProperty("verbose")
    VERBOSE
}
