package org.prebid.server.auction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
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

    private static final Logger logger = LoggerFactory.getLogger(OrtbTypesResolver.class);

    private static final String USER = "user";
    private static final String APP = "app";
    private static final String SITE = "site";
    private static final String BIDREQUEST_PREFIX = "bidrequest.";

    private static final Map<String, Set<String>> FIRST_ARRAY_ELEMENT_STANDARD_FIELDS;
    private static final Map<String, Set<String>> FIRST_ARRAY_ELEMENT_REQUEST_FIELDS;
    private static final Map<String, Set<String>> COMMA_SEPARATED_ELEMENT_FIELDS;

    static {
        FIRST_ARRAY_ELEMENT_REQUEST_FIELDS = new HashMap<>();
        FIRST_ARRAY_ELEMENT_REQUEST_FIELDS.put(USER, new HashSet<>(Collections.singleton("gender")));
        FIRST_ARRAY_ELEMENT_REQUEST_FIELDS.put(APP, new HashSet<>(Arrays.asList("id", "name", "bundle", "storeurl",
                "domain")));
        FIRST_ARRAY_ELEMENT_REQUEST_FIELDS.put(SITE, new HashSet<>(Arrays.asList("id", "name", "domain", "page",
                "ref", "search")));

        FIRST_ARRAY_ELEMENT_STANDARD_FIELDS = new HashMap<>();
        FIRST_ARRAY_ELEMENT_STANDARD_FIELDS.put(USER, new HashSet<>(Collections.singleton("gender")));
        FIRST_ARRAY_ELEMENT_STANDARD_FIELDS.put(APP, new HashSet<>(Arrays.asList("name", "bundle", "storeurl",
                "domain")));
        FIRST_ARRAY_ELEMENT_STANDARD_FIELDS.put(SITE, new HashSet<>(Arrays.asList("name", "domain", "page", "ref",
                "search")));

        COMMA_SEPARATED_ELEMENT_FIELDS = new HashMap<>();
        COMMA_SEPARATED_ELEMENT_FIELDS.put(USER, Collections.singleton("keywords"));
        COMMA_SEPARATED_ELEMENT_FIELDS.put(APP, Collections.singleton("keywords"));
        COMMA_SEPARATED_ELEMENT_FIELDS.put(SITE, Collections.singleton("keywords"));
    }

    /**
     * Resolves fields types inconsistency to ortb2 protocol for {@param bidRequest} for bidRequest level parameters
     * and bidderconfig.
     * Mutates both parameters, {@param fpdContainerNode} and {@param warnings}.
     */
    void normalizeBidRequest(JsonNode bidRequest, List<String> warnings) {
        normalizeRequestFpdFields(bidRequest, warnings);
        final JsonNode bidderConfigs = bidRequest.path("ext").path("prebid").path("bidderconfig");
        if (!bidderConfigs.isMissingNode() && bidderConfigs.isArray()) {
            for (JsonNode bidderConfig : bidderConfigs) {
                final JsonNode config = bidderConfig.path("config").path("fpd");
                if (!config.isMissingNode()) {
                    normalizeStandardFpdFields(config, warnings, "bidrequest.ext.prebid.bidderconfig");
                }
            }
        }
    }

    /**
     * Resolves fields types inconsistency to ortb2 protocol for {@param fpdContainerNode}.
     * Mutates both parameters, {@param fpdContainerNode} and {@param warnings}.
     */
    void normalizeStandardFpdFields(JsonNode fpdContainerNode, List<String> warnings, String nodePrefix) {
        final String normalizedNodePrefix = nodePrefix.endsWith(".") ? nodePrefix : nodePrefix.concat(".");
        if (fpdContainerNode != null && fpdContainerNode.isObject()) {
            final ObjectNode fpdContainerObjectNode = (ObjectNode) fpdContainerNode;
            updateWithNormalizedNode(fpdContainerObjectNode, USER, FIRST_ARRAY_ELEMENT_STANDARD_FIELDS,
                    COMMA_SEPARATED_ELEMENT_FIELDS, normalizedNodePrefix, warnings);
            updateWithNormalizedNode(fpdContainerObjectNode, APP, FIRST_ARRAY_ELEMENT_STANDARD_FIELDS,
                    COMMA_SEPARATED_ELEMENT_FIELDS, normalizedNodePrefix, warnings);
            updateWithNormalizedNode(fpdContainerObjectNode, SITE, FIRST_ARRAY_ELEMENT_STANDARD_FIELDS,
                    COMMA_SEPARATED_ELEMENT_FIELDS, normalizedNodePrefix, warnings);
        }
    }

    private void normalizeRequestFpdFields(JsonNode fpdContainerNode, List<String> warnings) {
        if (fpdContainerNode != null && fpdContainerNode.isObject()) {
            final ObjectNode fpdContainerObjectNode = (ObjectNode) fpdContainerNode;
            updateWithNormalizedNode(fpdContainerObjectNode, USER, FIRST_ARRAY_ELEMENT_REQUEST_FIELDS,
                    COMMA_SEPARATED_ELEMENT_FIELDS, BIDREQUEST_PREFIX, warnings);
            updateWithNormalizedNode(fpdContainerObjectNode, APP, FIRST_ARRAY_ELEMENT_REQUEST_FIELDS,
                    COMMA_SEPARATED_ELEMENT_FIELDS, BIDREQUEST_PREFIX, warnings);
            updateWithNormalizedNode(fpdContainerObjectNode, SITE, FIRST_ARRAY_ELEMENT_REQUEST_FIELDS,
                    COMMA_SEPARATED_ELEMENT_FIELDS, BIDREQUEST_PREFIX, warnings);
        }
    }

    private void updateWithNormalizedNode(ObjectNode containerNode, String nodeNameToNormalize,
                                          Map<String, Set<String>> firstArrayElementsFields,
                                          Map<String, Set<String>> commaSeparatedElementFields,
                                          String nodePrefix,
                                          List<String> warnings) {
        final JsonNode normalizedNode = normalizeNode(containerNode.get(nodeNameToNormalize), nodeNameToNormalize,
                firstArrayElementsFields, commaSeparatedElementFields, nodePrefix, warnings);
        if (normalizedNode != null) {
            containerNode.set(nodeNameToNormalize, normalizedNode);
        }
    }

    private JsonNode normalizeNode(JsonNode containerNode, String nodeName,
                                   Map<String, Set<String>> firstArrayElementsFields,
                                   Map<String, Set<String>> commaSeparatedElementFields,
                                   String nodePrefix,
                                   List<String> warnings) {
        if (containerNode != null && containerNode.isObject()) {
            final ObjectNode containerObjectNode = (ObjectNode) containerNode;

            CollectionUtils.emptyIfNull(firstArrayElementsFields.get(nodeName))
                    .forEach(fieldName -> updateWithNormalizedField(containerObjectNode, fieldName,
                            () -> toFirstElementTextNode(containerObjectNode, fieldName, nodeName, nodePrefix,
                                    warnings)));

            CollectionUtils.emptyIfNull(commaSeparatedElementFields.get(nodeName))
                    .forEach(fieldName -> updateWithNormalizedField(containerObjectNode, fieldName,
                            () -> toCommaSeparatedTextNode(containerObjectNode, fieldName, nodeName, nodePrefix,
                                    warnings)));
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
                                            String nodePrefix, List<String> warnings) {
        final JsonNode node = containerNode.get(fieldName);
        if (node == null || node.isNull() || node.isTextual()) {
            return node;
        }

        if (node.isArray()) {
            final ArrayNode arrayNode = (ArrayNode) node;
            if (!arrayNode.isEmpty() && isTextualArray(arrayNode)) {
                handleWarning(String.format("Incorrect type for first party data field %s%s.%s, expected is string, but"
                                + " was an array of strings. Converted to string by taking first element of array.",
                        nodePrefix, containerName, fieldName), warnings);
                return new TextNode(arrayNode.get(0).asText());
            }
        }
        final JsonNodeType nodeType = node.getNodeType();
        warnForExpectedStringArrayType(fieldName, containerName, warnings, nodePrefix, nodeType);
        return node;
    }

    private JsonNode toCommaSeparatedTextNode(ObjectNode containerNode, String fieldName, String containerName,
                                              String nodePrefix, List<String> warnings) {
        final JsonNode node = containerNode.get(fieldName);
        if (node == null || node.isNull() || node.isTextual()) {
            return node;
        }

        if (node.isArray()) {
            final ArrayNode arrayNode = (ArrayNode) node;
            if (!arrayNode.isEmpty() && isTextualArray(arrayNode)) {
                handleWarning(String.format("Incorrect type for first party data field %s%s.%s, expected is string, but"
                                + " was an array of strings. Converted to string by separating values with comma.",
                        nodePrefix, containerName, fieldName), warnings);
                return new TextNode(StreamSupport.stream(arrayNode.spliterator(), false)
                        .map(jsonNode -> (TextNode) jsonNode)
                        .map(TextNode::textValue)
                        .collect(Collectors.joining(",")));
            }
        }
        final JsonNodeType nodeType = node.getNodeType();
        warnForExpectedStringArrayType(fieldName, containerName, warnings, nodePrefix, nodeType);
        return node;
    }

    private void warnForExpectedStringArrayType(String fieldName, String containerName, List<String> warnings,
                                                String nodePrefix, JsonNodeType nodeType) {
        handleWarning(String.format("Incorrect type for first party data field %s%s.%s, expected strings, but was `%s`."
                        + " Failed to convert to correct type.", nodePrefix, containerName, fieldName,
                nodeType == JsonNodeType.ARRAY ? "ARRAY of different types" : nodeType.name()), warnings);
    }

    private static boolean isTextualArray(ArrayNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false).allMatch(JsonNode::isTextual);
    }

    private void handleWarning(String message, List<String> warnings) {
        warnings.add(message);

        // log only 1% of cases
        if (System.currentTimeMillis() % 100 == 0) {
            logger.warn(message);
        }
    }
}
