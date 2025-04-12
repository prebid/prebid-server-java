package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;

import java.util.Optional;

public class ConfigResolver {

    private final ObjectMapper mapper;

    private final OptableTargetingProperties globalProperties;

    public ConfigResolver(ObjectMapper mapper, OptableTargetingProperties globalProperties) {
        this.mapper = mapper;
        this.globalProperties = globalProperties;
    }

    public OptableTargetingProperties resolve(ObjectNode configNode) {
        final ObjectNode mergedNode = ((ObjectNode) mapper.valueToTree(globalProperties)).setAll(configNode);
        return parse(mergedNode).orElse(globalProperties);
    }

    private Optional<OptableTargetingProperties> parse(ObjectNode configNode) {
        try {
            return Optional.ofNullable(configNode)
                    .filter(node -> !node.isEmpty())
                    .map(node -> mapper.convertValue(node, OptableTargetingProperties.class));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
