package org.prebid.server.analytics.reporter.greenbids.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class GreenbidsUnifiedCode {

    @JsonProperty("value")
    String value;

    @JsonProperty("src")
    Source source;

    public enum Source {
        gpidSource("gpid"),
        storedRequestIdSource("storedRequestId"),
        adUnitCodeSource("adUnitCode");

        private final String value;

        Source(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
