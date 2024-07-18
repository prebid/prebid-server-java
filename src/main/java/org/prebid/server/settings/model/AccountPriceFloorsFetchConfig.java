package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AccountPriceFloorsFetchConfig {

    Boolean enabled;

    String url;

    @JsonAlias("timeout-ms")
    Long timeoutMs;

    @JsonAlias("max-file-size-kb")
    Long maxFileSizeKb;

    @JsonAlias("max-rules")
    Long maxRules;

    @JsonAlias("max-age-sec")
    Long maxAgeSec;

    @JsonAlias("period-sec")
    Long periodSec;
}
