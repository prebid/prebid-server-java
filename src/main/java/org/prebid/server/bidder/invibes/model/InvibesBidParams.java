package org.prebid.server.bidder.invibes.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
@Value
public class InvibesBidParams {

    @JsonProperty("PlacementIds")
    List<String> placementIds;

    @JsonProperty("BidVersion")
    String bidVersion;

    @JsonProperty("Properties")
    Map<String, InvibesPlacementProperty> properties;
}
