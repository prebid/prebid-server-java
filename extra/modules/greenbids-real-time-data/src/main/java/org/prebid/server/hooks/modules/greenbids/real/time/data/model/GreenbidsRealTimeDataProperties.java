package org.prebid.server.hooks.modules.greenbids.real.time.data.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class GreenbidsRealTimeDataProperties {
    @JsonProperty(value = "param1", required = true)
    String param1;

    @JsonProperty(value = "param2", required = true)
    Double param2;
}
