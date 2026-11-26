package org.prebid.server.bidder.optout.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Value
@Builder(toBuilder = true)
public class OptoutSlot {

    @JsonProperty("adSlot")
    String adSlot;
    String id;
    List<AdSize> sizes;
    Map<String, String> customs;
}
