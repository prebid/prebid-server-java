package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Service resolves types inconsistency and cast them if possible to ortb2 protocol.
 */
public class OrtbTypesResolver {

    private static final Logger logger = LoggerFactory.getLogger(OrtbTypesResolver.class);
    private static final ConditionalLogger ORTB_TYPES_RESOLVING_LOGGER =
            new ConditionalLogger("ortb_resolving_warnings", logger);

    private static final String USER = "user";
    private static final String APP = "app";
    private static final String SITE = "site";
    private static final String CONTEXT = "context";
    private static final String BIDREQUEST = "bidrequest";
    private static final String TARGETING = "targeting";
    private static final String UNKNOWN_REFERER = "unknown referer";
    private static final String DATA = "data";
    private static final String EXT = "ext";

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

    private final double logSamplingRate;

    private final JacksonMapper jacksonMapper;
    private final JsonMerger jsonMerger;

    public OrtbTypesResolver(double logSamplingRate, JacksonMapper jacksonMapper, JsonMerger jsonMerger) {
        this.logSamplingRate = logSamplingRate;
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
    }

    /**
     * Resolves fields types inconsistency to ortb2 protocol for {@param bidRequest} for bidRequest level parameters
     * and bidderconfig.
     * Mutates both parameters, {@param fpdContainerNode} and {@param warnings}.
     */
    public void normalizeBidRequest(JsonNode bidRequest, List<String> warnings, String referer) {
        final List<String> resolverWarnings = new ArrayList<>();
        final String rowOriginBidRequest = getOriginalRowContainerNode(bidRequest);
        normalizeRequestFpdFields(bidRequest, resolverWarnings);
        final JsonNode bidderConfigs = bidRequest.path("ext").path("prebid").path("bidderconfig");
        if (!bidderConfigs.isMissingNode() && bidderConfigs.isArray()) {
            for (JsonNode bidderConfig : bidderConfigs) {

                mergeFpdFieldsToOrtb2(bidderConfig);

                final JsonNode ortb2Config = bidderConfig.path("config").path("ortb2");
                if (!ortb2Config.isMissingNode()) {
                    normalizeStandardFpdFields(ortb2Config, resolverWarnings, "bidrequest.ext.prebid.bidderconfig");
                }
            }
        }
        processWarnings(resolverWarnings, warnings, rowOriginBidRequest, referer, BIDREQUEST);
    }

    private String getOriginalRowContainerNode(JsonNode bidRequest) {
        try {
            return jacksonMapper.mapper().writeValueAsString(bidRequest);
        } catch (JsonProcessingException e) {
            // should never happen
            throw new InvalidRequestException("Failed to decode container node to string");
        }
    }

    /**
     * Merges fpd fields into ortb2:
     * config.fpd.context -> config.ortb2.site
     * config.fpd.user -> config.ortb2.user
     */
    private void mergeFpdFieldsToOrtb2(JsonNode bidderConfig) {
        final JsonNode config = bidderConfig.path("config");
        final JsonNode configFpd = config.path("fpd");

        if (configFpd.isMissingNode()) {
            return;
        }

        final JsonNode configOrtb = config.path("ortb2");

        final JsonNode fpdContext = configFpd.get(CONTEXT);
        final JsonNode ortbSite = configOrtb.get(SITE);
        final JsonNode updatedOrtbSite = ortbSite == null
                ? fpdContext
                : fpdContext != null ? jsonMerger.merge(fpdContext, ortbSite) : null;

        final JsonNode fpdUser = configFpd.get(USER);
        final JsonNode ortbUser = configOrtb.get(USER);
        final JsonNode updatedOrtbUser = ortbUser == null
                ? fpdUser
                : fpdUser != null ? jsonMerger.merge(fpdUser, ortbUser) : null;

        if (updatedOrtbUser == null && updatedOrtbSite == null) {
            return;
        }

        final ObjectNode ortbObjectNode = configOrtb.isMissingNode()
                ? jacksonMapper.mapper().createObjectNode()
                : (ObjectNode) configOrtb;

        if (updatedOrtbSite != null) {
            ortbObjectNode.set(SITE, updatedOrtbSite);
        }

        if (updatedOrtbUser != null) {
            ortbObjectNode.set(USER, updatedOrtbUser);
        }

        ((ObjectNode) config).set("ortb2", ortbObjectNode);
    }

    /**
     * Resolves fields types inconsistency to ortb2 protocol for {@param targeting}.
     * Mutates both parameters, {@param targeting} and {@param warnings}.
     */
    public void normalizeTargeting(JsonNode targeting, List<String> warnings, String referer) {
        final List<String> resolverWarnings = new ArrayList<>();
        final String rowOriginTargeting = getOriginalRowContainerNode(targeting);
        normalizeStandardFpdFields(targeting, resolverWarnings, TARGETING);
        processWarnings(resolverWarnings, warnings, rowOriginTargeting, referer, TARGETING);
    }

    /**
     * Resolves fields types inconsistency to ortb2 protocol for {@param fpdContainerNode}.
     * Mutates both parameters, {@param fpdContainerNode} and {@param warnings}.
     */
    private void normalizeStandardFpdFields(JsonNode fpdContainerNode, List<String> warnings, String nodePrefix) {
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
            final String bidRequestPrefix = BIDREQUEST + ".";
            updateWithNormalizedNode(fpdContainerObjectNode, USER, FIRST_ARRAY_ELEMENT_REQUEST_FIELDS,
                    COMMA_SEPARATED_ELEMENT_FIELDS, bidRequestPrefix, warnings);
            updateWithNormalizedNode(fpdContainerObjectNode, APP, FIRST_ARRAY_ELEMENT_REQUEST_FIELDS,
                    COMMA_SEPARATED_ELEMENT_FIELDS, bidRequestPrefix, warnings);
            updateWithNormalizedNode(fpdContainerObjectNode, SITE, FIRST_ARRAY_ELEMENT_REQUEST_FIELDS,
                    COMMA_SEPARATED_ELEMENT_FIELDS, bidRequestPrefix, warnings);
        }
    }

    private void updateWithNormalizedNode(ObjectNode containerNode, String nodeNameToNormalize,
                                          Map<String, Set<String>> firstArrayElementsFields,
                                          Map<String, Set<String>> commaSeparatedElementFields,
                                          String nodePrefix, List<String> warnings) {
        final JsonNode normalizedNode = normalizeNode(containerNode.get(nodeNameToNormalize), nodeNameToNormalize,
                firstArrayElementsFields, commaSeparatedElementFields, nodePrefix, warnings);
        if (normalizedNode != null) {
            containerNode.set(nodeNameToNormalize, normalizedNode);
        } else {
            containerNode.remove(nodeNameToNormalize);
        }
    }

    private JsonNode normalizeNode(JsonNode containerNode, String nodeName,
                                   Map<String, Set<String>> firstArrayElementsFields,
                                   Map<String, Set<String>> commaSeparatedElementFields,
                                   String nodePrefix, List<String> warnings) {
        if (containerNode != null) {
            if (containerNode.isObject()) {
                final ObjectNode containerObjectNode = (ObjectNode) containerNode;

                CollectionUtils.emptyIfNull(firstArrayElementsFields.get(nodeName))
                        .forEach(fieldName -> updateWithNormalizedField(containerObjectNode, fieldName,
                                () -> toFirstElementTextNode(containerObjectNode, fieldName, nodeName, nodePrefix,
                                        warnings)));

                CollectionUtils.emptyIfNull(commaSeparatedElementFields.get(nodeName))
                        .forEach(fieldName -> updateWithNormalizedField(containerObjectNode, fieldName,
                                () -> toCommaSeparatedTextNode(containerObjectNode, fieldName, nodeName, nodePrefix,
                                        warnings)));

                normalizeDataExtension(containerObjectNode, nodeName, nodePrefix, warnings);
            } else {
                warnings.add("%s%s field ignored. Expected type is object, but was `%s`."
                        .formatted(nodePrefix, nodeName, containerNode.getNodeType().name()));
                return null;
            }
        }
        return containerNode;
    }

    private void updateWithNormalizedField(ObjectNode containerNode, String fieldName,
                                           Supplier<JsonNode> normalizationSupplier) {
        final JsonNode normalizedField = normalizationSupplier.get();
        if (normalizedField == null) {
            containerNode.remove(fieldName);
        } else {
            containerNode.set(fieldName, normalizedField);
        }
    }

    private JsonNode toFirstElementTextNode(ObjectNode containerNode,
                                            String fieldName,
                                            String containerName,
                                            String nodePrefix,
                                            List<String> warnings) {

        final JsonNode node = containerNode.get(fieldName);
        if (node == null || node.isNull() || node.isTextual()) {
            return node;
        }

        final boolean isArray = node.isArray();
        final ArrayNode arrayNode = isArray ? (ArrayNode) node : null;
        final boolean isTextualArray = arrayNode != null && isTextualArray(arrayNode) && !arrayNode.isEmpty();

        if (isTextualArray && !arrayNode.isEmpty()) {
            warnings.add("""
                    Incorrect type for first party data field %s%s.%s, expected is string, \
                    but was an array of strings. Converted to string by taking first element of array."""
                    .formatted(nodePrefix, containerName, fieldName));
            return new TextNode(arrayNode.get(0).asText());
        } else {
            warnForExpectedStringArrayType(fieldName, containerName, warnings, nodePrefix, node.getNodeType());
            return null;
        }
    }

    private JsonNode toCommaSeparatedTextNode(ObjectNode containerNode,
                                              String fieldName,
                                              String containerName,
                                              String nodePrefix,
                                              List<String> warnings) {

        final JsonNode node = containerNode.get(fieldName);
        if (node == null || node.isNull() || node.isTextual()) {
            return node;
        }

        final boolean isArray = node.isArray();
        final ArrayNode arrayNode = isArray ? (ArrayNode) node : null;
        final boolean isTextualArray = arrayNode != null && isTextualArray(arrayNode) && !arrayNode.isEmpty();

        if (isTextualArray) {
            warnings.add("""
                    Incorrect type for first party data field %s%s.%s, expected is string, \
                    but was an array of strings. Converted to string by separating values with comma."""
                    .formatted(nodePrefix, containerName, fieldName));

            return new TextNode(StreamSupport.stream(arrayNode.spliterator(), false)
                    .map(jsonNode -> (TextNode) jsonNode)
                    .map(TextNode::textValue)
                    .collect(Collectors.joining(",")));
        } else {
            warnForExpectedStringArrayType(fieldName, containerName, warnings, nodePrefix, node.getNodeType());
            return null;
        }
    }

    private void normalizeDataExtension(ObjectNode containerNode, String containerName, String nodePrefix,
                                        List<String> warnings) {
        final JsonNode data = containerNode.get(DATA);
        if (data == null || !data.isObject()) {
            return;
        }
        final JsonNode extData = containerNode.path(EXT).path(DATA);
        final JsonNode ext = containerNode.get(EXT);
        if (!extData.isNull() && !extData.isMissingNode()) {
            final JsonNode resolvedExtData = jsonMerger.merge(data, extData);
            ((ObjectNode) ext).set(DATA, resolvedExtData);
        } else {
            copyDataToExtData(containerNode, containerName, nodePrefix, warnings, data);
        }
        containerNode.remove(DATA);
    }

    private void copyDataToExtData(ObjectNode containerNode, String containerName, String nodePrefix,
                                   List<String> warnings, JsonNode data) {
        final JsonNode ext = containerNode.get(EXT);
        if (ext != null && ext.isObject()) {
            ((ObjectNode) ext).set(DATA, data);
        } else if (ext != null && !ext.isObject()) {
            warnings.add("""
                    Incorrect type for first party data field %s%s.%s, \
                    expected is object, but was %s. Replaced with object"""
                    .formatted(nodePrefix, containerName, EXT, ext.getNodeType()));
            containerNode.set(EXT, jacksonMapper.mapper().createObjectNode().set(DATA, data));
        } else {
            containerNode.set(EXT, jacksonMapper.mapper().createObjectNode().set(DATA, data));
        }
    }

    private void warnForExpectedStringArrayType(String fieldName, String containerName, List<String> warnings,
                                                String nodePrefix, JsonNodeType nodeType) {
        warnings.add("""
                Incorrect type for first party data field %s%s.%s, expected strings, \
                but was `%s`. Failed to convert to correct type.""".formatted(
                nodePrefix,
                containerName,
                fieldName,
                nodeType == JsonNodeType.ARRAY ? "ARRAY of different types" : nodeType.name()));
    }

    private static boolean isTextualArray(ArrayNode arrayNode) {
        return StreamSupport.stream(arrayNode.spliterator(), false).allMatch(JsonNode::isTextual);
    }

    private void processWarnings(List<String> resolverWarning, List<String> warnings, String containerValue,
                                 String referer, String containerName) {
        if (CollectionUtils.isNotEmpty(resolverWarning)) {
            warnings.addAll(updateWithWarningPrefix(resolverWarning));
            // log only 1% of cases
            ORTB_TYPES_RESOLVING_LOGGER.warn(
                    "WARNINGS: %s. \n Referer = %s and %s = %s".formatted(
                            String.join("\n", resolverWarning),
                            StringUtils.isNotBlank(referer) ? referer : UNKNOWN_REFERER,
                            containerName,
                            containerValue),
                    logSamplingRate);
        }
    }

    private List<String> updateWithWarningPrefix(List<String> resolverWarning) {
        return resolverWarning.stream().map(warning -> "WARNING: " + warning).toList();
    }
}
