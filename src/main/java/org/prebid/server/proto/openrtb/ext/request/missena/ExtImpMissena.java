package org.prebid.server.proto.openrtb.ext.request.missena;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder(toBuilder = true)
public class ExtImpMissena {

    @JsonProperty("apiKey")
    String apiKey;

    List<String> formats;

    String placement;

    @JsonProperty("test")
    String testMode;

    ObjectNode settings;
}
