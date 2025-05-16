package org.prebid.server.settings.model;

import lombok.Builder;
import lombok.Value;

@Builder(toBuilder = true)
@Value
public class AccountCacheConfig {

    Boolean enabled;
}
