package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Set;

@Value(staticConstructor = "of")
public class AccountCookieSyncConfig {

    @JsonProperty("default-limit")
    Integer defaultLimit;

    @JsonProperty("max-limit")
    Integer maxLimit;

    @JsonProperty("pri")
    Set<String> prioritizedBidders;

    @JsonProperty("coop-sync")
    AccountCoopSyncConfig coopSync;
}
