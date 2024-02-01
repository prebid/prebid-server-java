package org.prebid.server.proto.openrtb.ext.request.yearxero;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpYearxero {

    @JsonProperty("customParams")
    Map<String, JsonNode> customParams;
}
