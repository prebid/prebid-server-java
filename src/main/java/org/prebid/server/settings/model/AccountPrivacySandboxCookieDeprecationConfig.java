package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AccountPrivacySandboxCookieDeprecationConfig {

    Boolean enabled;

    @JsonProperty("ttlsec")
    Long ttlSec;
}
