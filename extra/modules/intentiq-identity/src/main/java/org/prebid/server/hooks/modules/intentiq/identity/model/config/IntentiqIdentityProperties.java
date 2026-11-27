package org.prebid.server.hooks.modules.intentiq.identity.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public final class IntentiqIdentityProperties {

    @JsonProperty("api-endpoint")
    String apiEndpoint;

    @JsonProperty("reports-endpoint")
    String reportsEndpoint;

    @JsonProperty("partner-id")
    String partnerId;

    /**
     * Partner ids a bid request may pick with {@code ext.intentiq.partnerId}. A request id that is not
     * listed is ignored and {@code partner-id} is used. Empty (the default): the request field is ignored.
     */
    @JsonProperty("allowed-partner-ids")
    List<String> allowedPartnerIds = List.of();

    Long timeout;

    /** Timeout of the fire-and-forget impression report; it does not hold the auction. */
    @JsonProperty("reports-timeout")
    Long reportsTimeout;

    CacheProperties cache = new CacheProperties();

    RedisProperties redis;

    AerospikeProperties aerospike;

    @JsonProperty("cache-max-size")
    long cacheMaxSize = 100_000L;

    // Module metrics are recorded by default; can opt out by setting this to false.
    @JsonProperty("metrics-enabled")
    boolean metricsEnabled = true;

    @JsonProperty("metrics-push")
    MetricsPushProperties metricsPush = new MetricsPushProperties();
}
