package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Set;

@Value(staticConstructor = "of")
public class AccountCoopSyncConfig {

    @JsonProperty("default")
    Boolean enabled;

    @JsonProperty("pri")
    Set<String> prioritizedBidders;
}
