package org.prebid.server.hooks.modules.optable.targeting.model.config;

import lombok.Data;

@Data
public class CacheProperties {

    boolean enabled = false;

    int ttlseconds = 86400;
}
