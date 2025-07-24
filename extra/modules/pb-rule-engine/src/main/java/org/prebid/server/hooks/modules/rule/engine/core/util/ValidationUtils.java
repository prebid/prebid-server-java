package org.prebid.server.hooks.modules.rule.engine.core.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.prebid.server.util.StreamUtil;

import java.util.Optional;
import java.util.function.Predicate;

public class ValidationUtils {

    public static void assertArrayOfStrings(ObjectNode config, String fieldName) {
        assertArrayOf(
                config,
                fieldName,
                JsonNode::isTextual,
                "Field '%s' is required and has to be an array of strings");
    }

    public static void assertArrayOfIntegers(ObjectNode config, String fieldName) {
        assertArrayOf(
                config,
                fieldName,
                JsonNode::isInt,
                "Field '%s' is required and has to be an array of integers");
    }

    private static void assertArrayOf(ObjectNode config,
                                      String fieldName,
                                      Predicate<JsonNode> predicate,
                                      String messageTemplate) {

        Optional.ofNullable(config)
                .map(node -> node.get(fieldName))
                .filter(JsonNode::isArray)
                .map(node -> (ArrayNode) node)
                .filter(Predicate.not(ArrayNode::isEmpty))
                .filter(node -> StreamUtil.asStream(node.elements()).allMatch(predicate))
                .orElseThrow(() -> new ConfigurationValidationException(messageTemplate.formatted(fieldName)));
    }

    public static void assertString(ObjectNode config, String fieldName) {
        assertField(config, fieldName, JsonNode::isTextual, "Field '%s' is required and has to be a string");
    }

    public static void assertInteger(ObjectNode config, String fieldName) {
        assertField(config, fieldName, JsonNode::isInt, "Field '%s' is required and has to be an integer");
    }

    private static void assertField(ObjectNode config,
                                    String fieldName,
                                    Predicate<JsonNode> predicate,
                                    String messageTemplate) {

        if (config == null || !config.has(fieldName) || !predicate.test(config.get(fieldName))) {
            throw new ConfigurationValidationException(messageTemplate.formatted(fieldName));
        }
    }

    public static void assertNoArgs(ObjectNode config) {
        if (config != null && !config.isEmpty()) {
            throw new ConfigurationValidationException("No arguments allowed");
        }
    }
}
