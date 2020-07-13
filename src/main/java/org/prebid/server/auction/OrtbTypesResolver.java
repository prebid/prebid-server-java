package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.commons.collections4.CollectionUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Service resolves types inconsistency and cast them if possible to ortb2 protocol.
 */
public class OrtbTypesResolver {

    private static final String USER = "user";
    private static final String APP = "app";
    private static final String SITE = "site";

    private static final Map<String, Set<String>> FIRST_ARRAY_ELEMENT_FIELDS;
    private static final Map<String, Set<String>> COMMA_SEPARATED_ELEMENT_FIELDS;

    static {
        FIRST_ARRAY_ELEMENT_FIELDS = new HashMap<>();
        FIRST_ARRAY_ELEMENT_FIELDS.put(USER, new HashSet<>(Collections.singleton("gender")));
        FIRST_ARRAY_ELEMENT_FIELDS.put(APP, new HashSet<>(Arrays.asList("id", "name", "bundle", "storeurl", "domain")));
        FIRST_ARRAY_ELEMENT_FIELDS.put(SITE, new HashSet<>(Arrays.asList("id", "name", "domain", "page", "ref",
                "search")));

        COMMA_SEPARATED_ELEMENT_FIELDS = new HashMap<>();
        COMMA_SEPARATED_ELEMENT_FIELDS.put(USER, Collections.singleton("keywords"));
        COMMA_SEPARATED_ELEMENT_FIELDS.put(APP, Collections.singleton("keywords"));
        COMMA_SEPARATED_ELEMENT_FIELDS.put(SITE, Collections.singleton("keywords"));
    }

    /**
     * Resolves fields types inconsistency to ortb2 protocol for {@param fpdContainerNode}.
     * Mutates both parameters, {@param fpdContainerNode} and {@param warnings}.
     */
    public void normalizeFpdFields(JsonNode fpdContainerNode, List<String> warnings) {
        if (fpdContainerNode != null && fpdContainerNode.isObject()) {
            final ObjectNode fpdContainerObjectNode = (ObjectNode) fpdContainerNode;
            updateWithNormalizedNode(fpdContainerObjectNode, USER, warnings);
            updateWithNormalizedNode(fpdContainerObjectNode, APP, warnings);
            updateWithNormalizedNode(fpdContainerObjectNode, SITE, warnings);
        }
    }

    private void updateWithNormalizedNode(ObjectNode containerNode, String nodeNameToNormalize, List<String> warnings) {
        final JsonNode normalizedUser = normalizeNode(containerNode.get(nodeNameToNormalize), nodeNameToNormalize,
                warnings);
        if (normalizedUser != null) {
            containerNode.set(nodeNameToNormalize, normalizedUser);
        }
    }

    private JsonNode normalizeNode(JsonNode containerNode, String nodeName, List<String> warnings) {
        if (containerNode != null && containerNode.isObject()) {
            final ObjectNode containerObjectNode = (ObjectNode) containerNode;

            CollectionUtils.emptyIfNull(FIRST_ARRAY_ELEMENT_FIELDS.get(nodeName))
                    .forEach(fieldName -> updateWithNormalizedField(containerObjectNode, fieldName,
                            () -> toFirstElementTextNode(containerObjectNode, fieldName, nodeName, warnings)));

            CollectionUtils.emptyIfNull(COMMA_SEPARATED_ELEMENT_FIELDS.get(nodeName))
                    .forEach(fieldName -> updateWithNormalizedField(containerObjectNode, fieldName,
                            () -> toCommaSeparatedTextNode(containerObjectNode, fieldName, nodeName, warnings)));
        }
        return containerNode;
    }

    private void updateWithNormalizedField(ObjectNode containerNode, String fieldName,
                                           Supplier<JsonNode> normalizationSupplier) {
        final JsonNode normalizedField = normalizationSupplier.get();
        if (normalizedField != null) {
            containerNode.set(fieldName, normalizedField);
        }
    }

    private JsonNode toFirstElementTextNode(ObjectNode containerNode, String fieldName, String containerName,
                                            List<String> warnings) {
        final JsonNode node = containerNode.get(fieldName);
        if (node == null || node.isNull() || node.isTextual()) {
            return node;
        }

        if (node.isArray()) {
            final ArrayNode arrayNode = (ArrayNode) node;
            if (!arrayNode.isEmpty() && isTextualArray(arrayNode)) {
                warnings.add(String.format("Incorrect type for first party data field %s.%s, expected is string, but"
                                + " was an array of strings. Converted to string by taking first element of array.",
                        containerName, fieldName));
                return new TextNode(arrayNode.get(0).asText());
            }
        }
        final JsonNodeType nodeType = node.getNodeType();
        warnForExpectedStringArrayType(fieldName, containerName, warnings, nodeType);
        return node;
    }

    private JsonNode toCommaSeparatedTextNode(ObjectNode containerNode, String fieldName, String containerName,
                                              List<String> warnings) {
        final JsonNode node = containerNode.get(fieldName);
        if (node == null || node.isNull() || node.isTextual()) {
            return node;
        }

        if (node.isArray()) {
            final ArrayNode arrayNode = (ArrayNode) node;
            if (!arrayNode.isEmpty() && isTextualArray(arrayNode)) {
                warnings.add(String.format("Incorrect type for first party data field %s.%s, expected is string, but"
                                + " was an array of strings. Converted to string by separating values with comma.",
                        containerName, fieldName));
                return new TextNode(StreamSupport.stream(arrayNode.spliterator(), false)
                        .map(jsonNode -> (TextNode) jsonNode)
                        .map(TextNode::textValue)
                        .collect(Collectors.joining(",")));
            }
        }
        final JsonNodeType nodeType = node.getNodeType();
        warnForExpectedStringArrayType(fieldName, containerName, warnings, nodeType);
        return node;
    }

    private void warnForExpectedStringArrayType(String fieldName, String containerName, List<String> warnings,
                                                JsonNodeType nodeType) {
        warnings.add(String.format("Incorrect type for first party data field %s.%s, expected strings, but was `%s`."
                        + " Failed to convert to correct type.", containerName, fieldName,
                nodeType == JsonNodeType.ARRAY ? "ARRAY of different types" : nodeType.name()));
    }

    private static boolean isTextualArray(ArrayNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false).allMatch(JsonNode::isTextual);
    }
}
