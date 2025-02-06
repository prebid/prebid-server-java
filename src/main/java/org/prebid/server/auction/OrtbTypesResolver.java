package org.prebid.server.auction;

import com.fasterxml.jackson.core.JsonPointer;
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
import org.prebid.server.util.StreamUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private static final String EXT = "ext";
    private static final String DATA = "data";
    private static final String CONFIG = "config";
    private static final String FPD = "fpd";
    private static final String ORTB2 = "ortb2";
    private static final String CONTEXT = "context";
    private static final String UNKNOWN_REFERER = "unknown referer";

    private static final JsonPointer EXT_PREBID_BIDDER_CONFIG = JsonPointer.valueOf("/ext/prebid/bidderconfig");
    private static final JsonPointer CONFIG_ORTB2 = JsonPointer.valueOf("/config/ortb2");
    private static final JsonPointer APP_BUNDLE = JsonPointer.valueOf("/app/bundle");
    private static final JsonPointer SITE_PAGE = JsonPointer.valueOf("/site/page");

    private static final Map<String, Set<String>> FIRST_ARRAY_ELEMENT_FIELDS;
    private static final Map<String, Set<String>> COMMA_SEPARATED_ELEMENT_FIELDS;

    static {
        FIRST_ARRAY_ELEMENT_FIELDS = Map.of(
                USER, Collections.singleton("gender"),
                APP, Set.of("id", "name", "bundle", "storeurl", "domain"),
                SITE, Set.of("id", "name", "domain", "page", "ref", "search"));

        COMMA_SEPARATED_ELEMENT_FIELDS = Map.of(
                USER, Collections.singleton("keywords"),
                APP, Collections.singleton("keywords"),
                SITE, Collections.singleton("keywords"));
    }

    private final double logSamplingRate;

    private final JacksonMapper jacksonMapper;
    private final JsonMerger jsonMerger;

    public OrtbTypesResolver(double logSamplingRate, JacksonMapper jacksonMapper, JsonMerger jsonMerger) {
        this.logSamplingRate = logSamplingRate;
        this.jacksonMapper = Objects.requireNonNull(jacksonMapper);
        this.jsonMerger = Objects.requireNonNull(jsonMerger);
    }

    public void normalizeBidRequest(JsonNode bidRequest, List<String> warnings, String referer) {
        final List<String> resolverWarnings = new ArrayList<>();

        normalizeFpdFields(bidRequest, "bidrequest.", resolverWarnings);

        final String source = source(bidRequest);
        final JsonNode bidderConfigs = bidRequest.at(EXT_PREBID_BIDDER_CONFIG);
        if (!bidderConfigs.isMissingNode() && bidderConfigs.isArray()) {
            for (JsonNode bidderConfig : bidderConfigs) {
                mergeFpdFieldsToOrtb2(bidderConfig, source);

                final JsonNode ortb2Config = bidderConfig.at(CONFIG_ORTB2);
                if (!ortb2Config.isMissingNode()) {
                    normalizeFpdFields(ortb2Config, "bidrequest.ext.prebid.bidderconfig.", resolverWarnings);
                }
            }
        }

        processWarnings(resolverWarnings, warnings, referer, "bidrequest", getOriginalRowContainerNode(bidRequest));
    }

    private void normalizeFpdFields(JsonNode fpdContainerNode, String prefix, List<String> warnings) {
        if (fpdContainerNode != null && fpdContainerNode.isObject()) {
            final ObjectNode fpdContainerObjectNode = (ObjectNode) fpdContainerNode;
            updateFpdWithNormalizedNode(fpdContainerObjectNode, USER, warnings, prefix);
            updateFpdWithNormalizedNode(fpdContainerObjectNode, APP, warnings, prefix);
            updateFpdWithNormalizedNode(fpdContainerObjectNode, SITE, warnings, prefix);
        }
    }

    private static String source(JsonNode bidRequest) {
        return Optional.ofNullable(stringAt(bidRequest, APP_BUNDLE))
                .orElseGet(() -> stringAt(bidRequest, SITE_PAGE));
    }

    private static String stringAt(JsonNode node, JsonPointer path) {
        final JsonNode at = node.at(path);
        return at.isMissingNode() || at.isNull() || !at.isTextual()
                ? null
                : at.textValue();
    }

    private void updateFpdWithNormalizedNode(ObjectNode containerNode,
                                             String nodeNameToNormalize,
                                             List<String> warnings,
                                             String nodePrefix) {

        updateWithNormalizedNode(
                containerNode,
                nodeNameToNormalize,
                normalizeNode(
                        containerNode.get(nodeNameToNormalize),
                        nodeNameToNormalize,
                        warnings,
                        nodePrefix));
    }

    private static void updateWithNormalizedNode(ObjectNode containerNode,
                                                 String fieldName,
                                                 JsonNode normalizedNode) {

        if (normalizedNode == null) {
            containerNode.remove(fieldName);
        } else {
            containerNode.set(fieldName, normalizedNode);
        }
    }

    private JsonNode normalizeNode(JsonNode containerNode, String nodeName, List<String> warnings, String nodePrefix) {
        if (containerNode == null) {
            return null;
        }
        if (!containerNode.isObject()) {
            warnings.add("%s%s field ignored. Expected type is object, but was `%s`."
                    .formatted(nodePrefix, nodeName, containerNode.getNodeType().name()));

            return null;
        }

        final ObjectNode containerObjectNode = (ObjectNode) containerNode;

        normalizeFields(
                FIRST_ARRAY_ELEMENT_FIELDS,
                nodeName,
                containerObjectNode,
                (name, node) -> toFirstElementTextNode(name, node, warnings, nodePrefix, nodeName));
        normalizeFields(
                COMMA_SEPARATED_ELEMENT_FIELDS,
                nodeName,
                containerObjectNode,
                (name, node) -> toCommaSeparatedTextNode(name, node, warnings, nodePrefix, nodeName));

        normalizeDataExtension(containerObjectNode, warnings, nodePrefix, nodeName);

        return containerNode;
    }

    private static void normalizeFields(Map<String, Set<String>> nodeNameToFields,
                                        String nodeName,
                                        ObjectNode containerObjectNode,
                                        BiFunction<String, JsonNode, JsonNode> fieldNormalizer) {

        nodeNameToFields.get(nodeName)
                .forEach(fieldName -> updateWithNormalizedNode(
                        containerObjectNode,
                        fieldName,
                        fieldNormalizer.apply(fieldName, containerObjectNode.get(fieldName))));
    }

    private static TextNode toFirstElementTextNode(String fieldName,
                                                   JsonNode fieldNode,
                                                   List<String> warnings,
                                                   String nodePrefix,
                                                   String containerName) {

        return toTextNode(
                fieldName,
                fieldNode,
                arrayNode -> arrayNode.get(0).asText(),
                warnings,
                nodePrefix,
                containerName,
                "Converted to string by taking first element of array.");
    }

    private static TextNode toTextNode(String fieldName,
                                       JsonNode fieldNode,
                                       Function<ArrayNode, String> mapper,
                                       List<String> warnings,
                                       String nodePrefix,
                                       String containerName,
                                       String action) {

        if (fieldNode == null || fieldNode.isNull()) {
            return null;
        }

        if (fieldNode.isTextual()) {
            return (TextNode) fieldNode;
        }

        final ArrayNode arrayNode = fieldNode.isArray() ? (ArrayNode) fieldNode : null;
        final boolean isTextualArray = arrayNode != null && !arrayNode.isEmpty() && isTextualArray(arrayNode);

        if (isTextualArray) {
            warnings.add("""
                    Incorrect type for first party data field %s%s.%s, expected is string, \
                    but was an array of strings. %s"""
                    .formatted(nodePrefix, containerName, fieldName, action));

            return new TextNode(mapper.apply(arrayNode));
        } else {
            warnForExpectedStringArrayType(warnings, nodePrefix, containerName, fieldName, fieldNode.getNodeType());
            return null;
        }
    }

    private static boolean isTextualArray(ArrayNode arrayNode) {
        return StreamUtil.asStream(arrayNode.iterator()).allMatch(JsonNode::isTextual);
    }

    private static void warnForExpectedStringArrayType(List<String> warnings,
                                                       String nodePrefix,
                                                       String containerName,
                                                       String fieldName,
                                                       JsonNodeType nodeType) {

        warnings.add("""
                Incorrect type for first party data field %s%s.%s, expected strings, \
                but was `%s`. Failed to convert to correct type.""".formatted(
                nodePrefix,
                containerName,
                fieldName,
                nodeType == JsonNodeType.ARRAY ? "ARRAY of different types" : nodeType.name()));
    }

    private static TextNode toCommaSeparatedTextNode(String fieldName,
                                                     JsonNode fieldNode,
                                                     List<String> warnings,
                                                     String nodePrefix,
                                                     String containerName) {

        return toTextNode(
                fieldName,
                fieldNode,
                arrayNode -> StreamUtil.asStream(arrayNode.spliterator())
                        .map(TextNode.class::cast)
                        .map(TextNode::textValue)
                        .collect(Collectors.joining(",")),
                warnings,
                nodePrefix,
                containerName,
                "Converted to string by separating values with comma.");
    }

    private void normalizeDataExtension(ObjectNode containerNode,
                                        List<String> warnings,
                                        String nodePrefix,
                                        String containerName) {

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
            copyDataToExtData(containerNode, data, warnings, nodePrefix, containerName);
        }

        containerNode.remove(DATA);
    }

    private void copyDataToExtData(ObjectNode containerNode,
                                   JsonNode data,
                                   List<String> warnings,
                                   String nodePrefix,
                                   String containerName) {

        final JsonNode ext = containerNode.get(EXT);
        if (ext == null) {
            createExtAndCopyData(containerNode, data);
        } else if (!ext.isObject()) {
            warnings.add("""
                    Incorrect type for first party data field %s%s.%s, \
                    expected is object, but was %s. Replaced with object"""
                    .formatted(nodePrefix, containerName, EXT, ext.getNodeType()));
            createExtAndCopyData(containerNode, data);
        } else {
            ((ObjectNode) ext).set(DATA, data);
        }
    }

    private void createExtAndCopyData(ObjectNode containerNode, JsonNode data) {
        containerNode.set(EXT, jacksonMapper.mapper().createObjectNode().set(DATA, data));
    }

    private void mergeFpdFieldsToOrtb2(JsonNode bidderConfig, String source) {
        final JsonNode config = bidderConfig.path(CONFIG);
        final JsonNode configFpd = config.path(FPD);

        if (configFpd.isMissingNode()) {
            return;
        }

        logDeprecatedFpdConfig(source);

        final JsonNode configOrtb = config.path(ORTB2);
        final JsonNode updatedOrtbSite = updatedOrtb2Node(configFpd, CONTEXT, configOrtb, SITE);
        final JsonNode updatedOrtbUser = updatedOrtb2Node(configFpd, USER, configOrtb, USER);

        if (updatedOrtbUser == null && updatedOrtbSite == null) {
            return;
        }

        final ObjectNode ortbObjectNode = configOrtb.isMissingNode()
                ? jacksonMapper.mapper().createObjectNode()
                : (ObjectNode) configOrtb;

        setIfNotNull(ortbObjectNode, SITE, updatedOrtbSite);
        setIfNotNull(ortbObjectNode, USER, updatedOrtbUser);

        ((ObjectNode) config).set(ORTB2, ortbObjectNode);
    }

    private void logDeprecatedFpdConfig(String source) {
        final String messagePart = source != null ? " on " + source : StringUtils.EMPTY;
        ORTB_TYPES_RESOLVING_LOGGER.warn("Usage of deprecated FPD config path" + messagePart, logSamplingRate);
    }

    private JsonNode updatedOrtb2Node(JsonNode configFpd, String fpdField, JsonNode configOrtb, String ortbField) {
        final JsonNode fpdNode = configFpd.get(fpdField);
        final JsonNode ortbNode = configOrtb.get(ortbField);
        return ortbNode == null
                ? fpdNode
                : fpdNode != null ? jsonMerger.merge(ortbNode, fpdNode) : null;
    }

    private static void setIfNotNull(ObjectNode destination, String fieldName, JsonNode data) {
        if (data != null) {
            destination.set(fieldName, data);
        }
    }

    private void processWarnings(List<String> resolverWarnings,
                                 List<String> warnings,
                                 String referer,
                                 String containerName,
                                 String containerValue) {

        if (CollectionUtils.isNotEmpty(resolverWarnings)) {
            warnings.addAll(updateWithWarningPrefix(resolverWarnings));

            ORTB_TYPES_RESOLVING_LOGGER.warn(
                    "WARNINGS: %s. \n Referer = %s and %s = %s".formatted(
                            String.join("\n", resolverWarnings),
                            StringUtils.isNotBlank(referer) ? referer : UNKNOWN_REFERER,
                            containerName,
                            containerValue),
                    logSamplingRate);
        }
    }

    private static List<String> updateWithWarningPrefix(List<String> resolverWarning) {
        return resolverWarning.stream().map(warning -> "WARNING: " + warning).toList();
    }

    private String getOriginalRowContainerNode(JsonNode bidRequest) {
        try {
            return jacksonMapper.mapper().writeValueAsString(bidRequest);
        } catch (JsonProcessingException e) {
            // should never happen
            throw new InvalidRequestException("Failed to decode container node to string");
        }
    }

    public void normalizeTargeting(JsonNode targeting, List<String> warnings, String referer) {
        final List<String> resolverWarnings = new ArrayList<>();
        normalizeFpdFields(targeting, "targeting.", resolverWarnings);
        processWarnings(resolverWarnings, warnings, referer, "targeting", getOriginalRowContainerNode(targeting));
    }
}
