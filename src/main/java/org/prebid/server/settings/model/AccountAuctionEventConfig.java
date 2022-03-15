package org.prebid.server.settings.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import lombok.Builder;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Value(staticConstructor = "of")
@Builder(toBuilder = true)
public class AccountAuctionEventConfig {

    Map<String, Boolean> events = new HashMap<>();

    @JsonAnyGetter
    public Map<String, Boolean> getEvents() {
        return Collections.unmodifiableMap(events);
    }

    @JsonAnySetter
    public void addEvent(String key, Boolean value) {
        events.put(resolveKey(key), value);
    }

    private static String resolveKey(String key) {
        if (StringUtils.equalsIgnoreCase("pbjs", key)) {
            return "web";
        }

        return key;
    }
}
