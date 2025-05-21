package org.prebid.server.hooks.modules.rule.engine.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.util.CollectionUtils;

import java.util.List;

public class ValidationUtils {

    public static void assertArrayOfStrings(List<JsonNode> configurationArguments) {
        if (CollectionUtils.isEmpty(configurationArguments)
                || !configurationArguments.stream().allMatch(JsonNode::isTextual)) {
            throw new ConfigurationValidationException("Array of strings required");
        }
    }

    public static void assertNoArgs(List<JsonNode> configurationArguments) {
        if (configurationArguments != null) {
            throw new ConfigurationValidationException("No arguments allowed");
        }
    }
}
