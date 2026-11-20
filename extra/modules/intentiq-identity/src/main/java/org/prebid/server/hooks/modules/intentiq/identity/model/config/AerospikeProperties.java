package org.prebid.server.hooks.modules.intentiq.identity.model.config;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public final class AerospikeProperties {

    String host;

    Integer port = 3000;

    String namespace;

    String set;

    /** Enterprise Edition security user, if security is enabled. */
    String user;

    String password;

    /** Timeout of the initial cluster connection. */
    @JsonProperty("connect-timeout-ms")
    int connectTimeoutMs = 1_000;

    /** Total timeout of one read, write or delete. */
    @JsonProperty("timeout-ms")
    int timeoutMs = 100;

    /** Max async connections per cluster node. */
    @JsonProperty("max-connections-per-node")
    int maxConnectionsPerNode = 100;

    /** Threads of the client's event loop. */
    @JsonProperty("event-loop-threads")
    int eventLoopThreads = 2;
}
