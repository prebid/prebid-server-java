package org.prebid.server.hooks.modules.optable.targeting.model.config;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CacheProperties {

    boolean enabled = false;

    int ttlseconds = 86400;

    public CacheProperties() {
    }

    public CacheProperties(boolean enabled, int ttlseconds) {
        this.enabled = enabled;
        this.ttlseconds = ttlseconds;
    }
}
