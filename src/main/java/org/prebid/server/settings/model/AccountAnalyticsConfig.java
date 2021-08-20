package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Value;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Value(staticConstructor = "of")
public class AccountAnalyticsConfig {

    private static final Map<String, Boolean> FALLBACK_AUCTION_EVENTS;

    static {
        final Map<String, Boolean> events = new HashMap<>();
        events.put("web", false);
        events.put("amp", true);
        events.put("app", true);

        FALLBACK_AUCTION_EVENTS = Collections.unmodifiableMap(events);
    }

    @JsonProperty("auction-events")
    Map<String, Boolean> auctionEvents;

    Map<String, ObjectNode> modules;

    public static Map<String, Boolean> fallbackAuctionEvents() {
        return FALLBACK_AUCTION_EVENTS;
    }
}
