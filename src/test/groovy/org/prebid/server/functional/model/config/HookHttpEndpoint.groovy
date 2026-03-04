package org.prebid.server.functional.model.config

import com.fasterxml.jackson.annotation.JsonValue
import groovy.transform.ToString

@ToString
enum HookHttpEndpoint {

    AUCTION("/openrtb2/auction"),
    AUCTION_GET("GET /openrtb2/auction"),
    AUCTION_POST("POST /openrtb2/auction"),

    AMP("/openrtb2/amp"),
    AMP_GET("GET /openrtb2/amp"),
    AMP_POST("POST /openrtb2/amp"),

    VIDEO("/openrtb2/video"),
    VIDEO_GET("GET /openrtb2/video"),
    VIDEO_POST("POST /openrtb2/video"),

    @JsonValue
    final String value

    HookHttpEndpoint(String value) {
        this.value = value
    }

    @Override
    String toString() {
        value
    }
}
