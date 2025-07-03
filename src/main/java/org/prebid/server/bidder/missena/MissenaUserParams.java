package org.prebid.server.bidder.missena;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode; // Changed import
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class MissenaUserParams {

    List<String> formats;

    String placement;

    @JsonProperty("test")
    String testMode;

    ObjectNode settings;
}

