package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

@Builder
@Value
public class AccountTargetingConfig {

    @JsonProperty("includewinners")
    Boolean includeWinners;

    @JsonProperty("includebidderkeys")
    Boolean includeBidderKeys;

    @JsonProperty("includeformat")
    Boolean includeFormat;

    @JsonProperty("preferdeals")
    Boolean preferDeals;

    @JsonProperty("alwaysincludedeals")
    Boolean alwaysIncludeDeals;

    @JsonProperty("prefix")
    String prefix;
}
