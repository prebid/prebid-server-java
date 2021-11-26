package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AccountPriceFloorsFetchConfig {

    Boolean enabled;

    String url;

    @JsonProperty("timeout-ms")
    Long timeout;

    @JsonProperty("max-file-size-kb")
    Long maxFileSize;

    @JsonProperty("max-rules")
    Integer maxRules;

    @JsonProperty("max-age-sec")
    Integer maxAgeSec;

    @JsonProperty("period-sec")
    Integer periodSec;
}
