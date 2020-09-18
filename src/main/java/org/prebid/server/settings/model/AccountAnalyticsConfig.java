package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Map;

@Value(staticConstructor = "of")
public class AccountAnalyticsConfig {

    @JsonProperty("auction-events")
    Map<String, Boolean> auctionEvents;
}
