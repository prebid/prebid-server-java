package org.prebid.server.hooks.modules.optable.targeting.model.config;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CacheProperties {

    private boolean enabled = false;

    private int ttlseconds = 86400;
}
