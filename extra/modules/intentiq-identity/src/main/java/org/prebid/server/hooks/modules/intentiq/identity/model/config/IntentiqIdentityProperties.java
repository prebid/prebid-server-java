package org.prebid.server.hooks.modules.intentiq.identity.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public final class IntentiqIdentityProperties {

    @JsonProperty("api-endpoint")
    String apiEndpoint;

    @JsonProperty("reports-endpoint")
    String reportsEndpoint;

    @JsonProperty("partner-id")
    String partnerId;

    Long timeout;

    CacheProperties cache = new CacheProperties();

    RedisProperties redis;

    @JsonProperty("cache-max-size")
    long cacheMaxSize = 100_000L;

    // Module metrics are recorded by default; can opt out by setting this to false.
    @JsonProperty("metrics-enabled")
    boolean metricsEnabled = true;
}
