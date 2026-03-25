package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import lombok.Data;

@Data
public class RedisConnectionConfig {

    /** Host value of the Redis server */
    String host;

    /** Port value of the Redis server */
    int port;

    /** User password value of the Redis server */
    String password;
}
