package org.prebid.server.hooks.modules.intentiq.identity.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CacheProperties {

    private boolean enabled;

    /** Positive TTL used when the IntentIQ API omits {@code cttl}. */
    private int ttlseconds = 43_200;

    /** Max number of alias keys derived per request (guards against eid-stuffed requests). */
    @JsonProperty("max-keys")
    private int maxKeys = 10;

    /** Upper bound on positive TTL for first-party id keys (pubcid, MAID, other eids). */
    @JsonProperty("ttl-ceiling-first-party-seconds")
    private int ttlCeilingFirstPartySeconds = 86_400;

    /** Upper bound on positive TTL for third-party id keys (intentiq.com). */
    @JsonProperty("ttl-ceiling-third-party-seconds")
    private int ttlCeilingThirdPartySeconds = 43_200;

    /** Upper bound on positive TTL for the probabilistic device-composite key. */
    @JsonProperty("ttl-ceiling-device-seconds")
    private int ttlCeilingDeviceSeconds = 3_600;

    /** Short TTL for the negative (unresolvable id) sentinel. */
    @JsonProperty("negative-ttl-seconds")
    private int negativeTtlSeconds = 120;

    /**
     * TTL for the IN_PROGRESS marker written while a resolution call is in flight. A failed call
     * removes its marker at once; this TTL only bounds a marker whose call never completed (e.g. the
     * server stopped mid-call), so it needs to outlive the API timeout and little more.
     */
    @JsonProperty("in-progress-ttl-seconds")
    private int inProgressTtlSeconds = 30;

    /**
     * Upper bound on an L2 (shared store) read. A slower or hung store is treated as a miss and the
     * request falls through to a live API call instead of waiting for the hook timeout.
     */
    @JsonProperty("l2-timeout-ms")
    private long l2TimeoutMs = 30;
}
