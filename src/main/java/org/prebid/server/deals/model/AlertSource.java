package org.prebid.server.deals.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AlertSource {

    String env;

    @JsonProperty("data-center")
    String dataCenter;

    String region;

    String system;

    @JsonProperty("sub-system")
    String subSystem;

    @JsonProperty("host-id")
    String hostId;
}
