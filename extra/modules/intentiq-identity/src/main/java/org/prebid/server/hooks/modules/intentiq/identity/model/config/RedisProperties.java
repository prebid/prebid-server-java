package org.prebid.server.hooks.modules.intentiq.identity.model.config;

import lombok.Data;

@Data
public final class RedisProperties {

    String host;

    Integer port;

    String password;
}
