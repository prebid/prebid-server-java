package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Set;

@Value(staticConstructor = "of")
public class AccountCookieSyncConfig {

    @JsonAlias("default-limit")
    Integer defaultLimit;

    @JsonAlias("max-limit")
    Integer maxLimit;

    @JsonProperty("pri")
    Set<String> prioritizedBidders;

    @JsonAlias("coop-sync")
    AccountCoopSyncConfig coopSync;
}
