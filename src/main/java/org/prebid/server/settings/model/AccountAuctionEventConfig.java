package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Builder;
import lombok.Value;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class AccountAuctionEventConfig {

    Map<String, Boolean> events = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Boolean> getEvents() {
        return Collections.unmodifiableMap(events);
    }

    @JsonAnySetter
    public void addEvent(String key, Boolean value) {
        events.put(key, value);
    }
}
