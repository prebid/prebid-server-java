package org.prebid.server.proto.openrtb.ext.request.sparteo;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;

import java.util.HashMap;
import java.util.Map;

@Value(staticConstructor = "of")
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtImpSparteo {

    @JsonProperty("networkId")
    String networkId;

    @JsonProperty("custom1")
    String custom1;

    @JsonProperty("custom2")
    String custom2;

    @JsonProperty("custom3")
    String custom3;

    @JsonProperty("custom4")
    String custom4;

    @JsonProperty("custom5")
    String custom5;

    Map<String, JsonNode> additionalProperties = new HashMap<>();

    @JsonAnySetter
    public void addAdditionalProperty(String key, JsonNode value) {
        additionalProperties.put(key, value);
    }

    @JsonAnyGetter
    public Map<String, JsonNode> getAdditionalProperties() {
        return additionalProperties;
    }
}
