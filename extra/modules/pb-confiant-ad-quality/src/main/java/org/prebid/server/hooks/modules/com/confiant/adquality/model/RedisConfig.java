package org.prebid.server.hooks.modules.com.confiant.adquality.model;

import lombok.Data;

@Data
public class RedisConfig {

    /** Redis replica with write access */
    RedisConnectionConfig writeNode;

    /** Redis replica with read only access */
    RedisConnectionConfig readNode;
}
