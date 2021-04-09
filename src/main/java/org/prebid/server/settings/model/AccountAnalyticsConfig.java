package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Value(staticConstructor = "of")
public class AccountAnalyticsConfig {

    private static final AccountAnalyticsConfig FALLBACK;

    static {
        final Map<String, Boolean> events = new HashMap<>();
        events.put("web", false);
        events.put("amp", true);
        events.put("app", true);

        FALLBACK = AccountAnalyticsConfig.of(Collections.unmodifiableMap(events));
    }

    @JsonProperty("auction-events")
    Map<String, Boolean> auctionEvents;

    public static AccountAnalyticsConfig fallback() {
        return FALLBACK;
    }
}
