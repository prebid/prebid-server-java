package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

@Value(staticConstructor = "of")
public class AccountCookieSyncConfig {

    @JsonProperty("default-limit")
    Integer defaultLimit;

    @JsonProperty("max-limit")
    Integer maxLimit;

    @JsonProperty("default-coop-sync")
    Boolean defaultCoopSync;
}
