package org.prebid.server.proto.openrtb.ext;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@ToString
@EqualsAndHashCode
public abstract class FlexibleExtension {

    public static final TypeReference<Map<String, JsonNode>> PROPERTIES_TYPE_REF =
            new TypeReference<Map<String, JsonNode>>() {
            };

    private final Map<String, JsonNode> properties = new HashMap<>();

    public JsonNode getProperty(String property) {
        return properties.get(property);
    }

    public boolean containsProperty(String property) {
        return properties.containsKey(property);
    }

    @JsonAnyGetter
    public Map<String, JsonNode> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    @JsonAnySetter
    public void addProperty(String key, JsonNode value) {
        properties.put(key, value);
    }

    public void addProperties(Map<String, JsonNode> properties) {
        this.properties.putAll(properties);
    }
}
