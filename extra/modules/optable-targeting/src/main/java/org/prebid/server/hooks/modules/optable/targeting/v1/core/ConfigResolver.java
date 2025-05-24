package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.json.JsonMerger;

import java.util.Objects;
import java.util.Optional;

public class ConfigResolver {

    private final ObjectMapper mapper;
    private final JsonMerger jsonMerger;
    private final OptableTargetingProperties globalProperties;
    private final JsonNode globalPropertiesObjectNode;

    public ConfigResolver(ObjectMapper mapper, JsonMerger jsonMerger, OptableTargetingProperties globalProperties) {
        this.mapper = Objects.requireNonNull(mapper);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
        this.globalProperties = Objects.requireNonNull(globalProperties);
        this.globalPropertiesObjectNode = Objects.requireNonNull(mapper.valueToTree(globalProperties));
    }

    public OptableTargetingProperties resolve(ObjectNode configNode) {
        final JsonNode mergedNode = jsonMerger.merge(configNode, globalPropertiesObjectNode);
        return parse(mergedNode).orElse(globalProperties);
    }

    private Optional<OptableTargetingProperties> parse(JsonNode configNode) {
        try {
            return Optional.ofNullable(configNode)
                    .filter(node -> !node.isEmpty())
                    .map(node -> mapper.convertValue(node, OptableTargetingProperties.class));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
