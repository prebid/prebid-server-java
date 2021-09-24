package org.prebid.server.proto.openrtb.ext.request.admixer;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

@Value
@AllArgsConstructor(staticName = "of")
public class ExtImpAdmixer {

    String zone;

    @JsonProperty("customFloor")
    BigDecimal customFloor;

    @JsonProperty("customParams")
    Map<String, JsonNode> customParams;
}
