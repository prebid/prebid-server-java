package org.prebid.server.settings.model;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class AccountDebugConfig {

    Boolean allowed;
}
