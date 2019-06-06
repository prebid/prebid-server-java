package org.prebid.server.bidder.openx.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class OpenxImpExt {

    @JsonProperty("customParams")
    Map<String, JsonNode> customParams;
}
