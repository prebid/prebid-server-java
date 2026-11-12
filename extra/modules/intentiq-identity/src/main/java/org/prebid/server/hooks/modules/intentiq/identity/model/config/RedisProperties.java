package org.prebid.server.hooks.modules.intentiq.identity.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public final class RedisProperties {

    String host;

    Integer port;

    String username;

    String password;

    /** Connect over TLS (e.g. ElastiCache / Memorystore with in-transit encryption). */
    boolean ssl;

    /** Max pooled connections. The Vert.x default (6) exhausts at a few hundred auctions per second. */
    @JsonProperty("max-pool-size")
    int maxPoolSize = 32;

    /** Max commands queued for a free connection before a command fails fast. */
    @JsonProperty("max-pool-waiting")
    int maxPoolWaiting = 1024;
}
