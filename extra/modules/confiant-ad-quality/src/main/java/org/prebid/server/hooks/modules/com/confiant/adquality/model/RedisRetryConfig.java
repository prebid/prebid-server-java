package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import lombok.Data;

@Data
public class RedisRetryConfig {

    /** Maximum attempts with short interval value to try to reconnect to Confiant's Redis server in case any connection error happens */
    int shortIntervalAttempts;

    /** Short time interval in milliseconds after which another one attempt to connect to Redis will be executed */
    int shortInterval;

    /** Maximum attempts with long interval value to try to reconnect to Confiant's Redis server in case any connection error happens. This attempts are used when short-attempts were not successful */
    int longIntervalAttempts;

    /** Long time interval in milliseconds after which another one attempt to connect to Redis will be executed */
    int longInterval;
}
