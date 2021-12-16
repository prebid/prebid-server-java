package org.prebid.server.deals.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AlertSource {

    private String env;

    @JsonProperty("data-center")
    private String dataCenter;

    private String region;

    private String system;

    @JsonProperty("sub-system")
    private String subSystem;

    @JsonProperty("host-id")
    private String hostId;
}
