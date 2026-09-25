package org.prebid.server.bidder.missena;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
public class MissenaUserParams {

    @JsonProperty("apiKey")
    String apiKey;

    List<String> formats;

    String placement;

    String sample;

    ObjectNode settings;
}
