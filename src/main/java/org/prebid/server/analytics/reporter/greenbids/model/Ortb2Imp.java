package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class Ortb2Imp {

    @JsonProperty("ext")
    private Ext ext;

    @Builder(toBuilder = true)
    @Value
    public static class Ext {
        @JsonProperty("greenbids")
        private Greenbids greenbids;

        @JsonProperty("tid")
        private String tid;
    }
}
