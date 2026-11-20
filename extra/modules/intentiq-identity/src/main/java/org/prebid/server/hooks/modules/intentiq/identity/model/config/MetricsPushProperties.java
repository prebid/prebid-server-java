package org.prebid.server.hooks.modules.intentiq.identity.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Pushes the module's metrics to an IntentIQ endpoint in the Prometheus text format. Off unless both
 * {@code url} and {@code token} are set. Host level only.
 */
@Data
@NoArgsConstructor
public final class MetricsPushProperties {

    /** Full import URL, given by IntentIQ. */
    String url;

    /** Bearer token, given by IntentIQ. Keep it out of files: reference an environment variable. */
    String token;

    /** Label that tells this server apart from the others. Defaults to the HOSTNAME variable or host name. */
    String instance;

    @JsonProperty("interval-seconds")
    int intervalSeconds = 60;

    @JsonProperty("timeout-ms")
    long timeoutMs = 5_000L;
}
