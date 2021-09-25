package org.prebid.server.bidder.grid;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.grid.model.KeywordSegment;
import org.prebid.server.bidder.grid.model.Keywords;
import org.prebid.server.bidder.grid.model.KeywordsPublisherItem;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GridKeywordsUtil {

    private static final TypeReference<Map<String, JsonNode>> MAP_TYPE_REF =
            new TypeReference<Map<String, JsonNode>>() {
            };

    private GridKeywordsUtil() {
    }

    public static Map<String, JsonNode> modifyWithKeywords(Map<String, JsonNode> extRequestProperties,
                                                           Keywords keywords,
                                                           JacksonMapper mapper) {

        final JsonNode keywordsJsonNode = extRequestProperties.get("keywords");
        final ObjectNode keywordsNode = keywordsJsonNode != null && keywordsJsonNode.isObject()
                ? (ObjectNode) keywordsJsonNode
                : mapper.mapper().createObjectNode();

        setIfValueNotNullOrRemove(keywordsNode, "user", clearObjectNode(keywords.getUser()));
        setIfValueNotNullOrRemove(keywordsNode, "site", clearObjectNode(keywords.getSite()));

        final Map<String, JsonNode> modifiedExtRequestProperties = new HashMap<>(extRequestProperties);
        if (!keywordsNode.isEmpty()) {
            modifiedExtRequestProperties.put("keywords", keywordsNode);
        } else {
            modifiedExtRequestProperties.remove("keywords");
        }
        return modifiedExtRequestProperties;
    }

    private static void setIfValueNotNullOrRemove(ObjectNode node, String key, JsonNode value) {
        if (value != null) {
            node.set(key, value);
        } else {
            node.remove(key);
        }
    }

    public static Keywords resolveKeywordsFromOpenRtb(String userKeywords, String siteKeywords, JacksonMapper mapper) {
        return Keywords.of(
                resolveKeywordsSectionFromOpenRtb(userKeywords, mapper),
                resolveKeywordsSectionFromOpenRtb(siteKeywords, mapper));
    }

    public static ObjectNode resolveKeywordsSectionFromOpenRtb(String keywords, JacksonMapper mapper) {
        final List<KeywordSegment> segments = Arrays.stream(keywords.split(","))
                .filter(StringUtils::isNotEmpty)
                .map(keyword -> KeywordSegment.of("keywords", keyword))
                .collect(Collectors.toList());

        final ObjectNode publisherNode = mapper.mapper().createObjectNode();
        if (!segments.isEmpty()) {
            final List<KeywordsPublisherItem> publisherItems = Collections.singletonList(
                    KeywordsPublisherItem.of("keywords", segments));
            return publisherNode.set("ortb2", mapper.mapper().valueToTree(publisherItems));
        }
        return publisherNode;
    }

    public static Keywords resolveKeywords(Keywords keywords, JacksonMapper mapper) {
        return keywords == null
                ? Keywords.empty()
                : Keywords.of(
                resolveKeywordsSection(keywords.getUser(), mapper),
                resolveKeywordsSection(keywords.getSite(), mapper));
    }

    public static ObjectNode resolveKeywordsSection(ObjectNode sectionNode, JacksonMapper mapper) {
        if (sectionNode == null) {
            return null;
        }

        final ObjectNode resolvedSectionNode = mapper.mapper().createObjectNode();
        final Map<String, JsonNode> sectionMap = jsonNodeToMap(sectionNode, mapper);

        for (Map.Entry<String, JsonNode> entry : sectionMap.entrySet()) {
            JsonNode publisherJsonNode = entry.getValue();
            if (publisherJsonNode != null && publisherJsonNode.isArray()) {
                final List<KeywordsPublisherItem> publisherKeywords =
                        resolvePublisherKeywords(publisherJsonNode, mapper);
                if (!publisherKeywords.isEmpty()) {
                    resolvedSectionNode.set(entry.getKey(), mapper.mapper().valueToTree(publisherKeywords));
                }
            }
        }
        return resolvedSectionNode;
    }

    public static List<KeywordsPublisherItem> resolvePublisherKeywords(JsonNode publisherNode, JacksonMapper mapper) {
        final List<KeywordsPublisherItem> publishersKeywords = new ArrayList<>();
        final Iterator<JsonNode> publisherNodeElements = publisherNode.elements();

        while (publisherNodeElements.hasNext()) {
            final JsonNode publisherValueNode = publisherNodeElements.next();
            final JsonNode publisherNameNode = publisherValueNode.get("name");
            final JsonNode segmentsNode = publisherValueNode.get("segments");

            if (publisherNameNode != null && publisherNameNode.isTextual()) {
                final List<KeywordSegment> segments = new ArrayList<>(resolvePublisherSegments(segmentsNode));
                segments.addAll(resolveAlternativePublisherSegments(publisherValueNode, mapper));

                if (!segments.isEmpty()) {
                    publishersKeywords.add(KeywordsPublisherItem.of(publisherNameNode.asText(), segments));
                }
            }
        }
        return publishersKeywords;
    }

    public static List<KeywordSegment> resolvePublisherSegments(JsonNode segmentsNode) {
        final List<KeywordSegment> parsedSegments = new ArrayList<>();
        if (segmentsNode == null || !segmentsNode.isArray()) {
            return parsedSegments;
        }

        for (Iterator<JsonNode> it = segmentsNode.elements(); it.hasNext();) {
            final KeywordSegment keywordSegment = resolvePublisherSegment(it.next());
            if (keywordSegment != null) {
                parsedSegments.add(keywordSegment);
            }
        }
        return parsedSegments;
    }

    public static KeywordSegment resolvePublisherSegment(JsonNode segmentNode) {
        final JsonNode nameNode = segmentNode.get("name");
        final String name = nameNode != null && nameNode.isTextual() ? nameNode.asText() : null;
        final JsonNode valueNode = segmentNode.get("value");
        final String value = valueNode != null && valueNode.isTextual() ? valueNode.asText() : null;

        return StringUtils.isNotEmpty(name) && StringUtils.isNotEmpty(value)
                ? KeywordSegment.of(name, value)
                : null;
    }

    public static List<KeywordSegment> resolveAlternativePublisherSegments(JsonNode publisherValueNode,
                                                                           JacksonMapper mapper) {
        return jsonNodeToMap(publisherValueNode, mapper).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(GridKeywordsUtil::isValidPublisherEntry)
                .flatMap(GridKeywordsUtil::mapPublisherEntryToKeywordsStream)
                .collect(Collectors.toList());
    }

    private static boolean isValidPublisherEntry(Map.Entry<String, JsonNode> publisherEntry) {
        final JsonNode publisherEntryValue = publisherEntry.getValue();
        return publisherEntryValue != null && publisherEntryValue.isArray();
    }

    private static Stream<KeywordSegment> mapPublisherEntryToKeywordsStream(
            Map.Entry<String, JsonNode> publisherEntry) {

        final List<KeywordSegment> keywordSegments = new ArrayList<>();
        final Iterator<JsonNode> publisherEntryElements = publisherEntry.getValue().elements();

        while (publisherEntryElements.hasNext()) {
            final JsonNode currentNode = publisherEntryElements.next();
            if (currentNode.isTextual()) {
                keywordSegments.add(KeywordSegment.of(publisherEntry.getKey(), currentNode.asText()));
            }
        }
        return keywordSegments.stream();
    }

    public static Keywords merge(JacksonMapper mapper, Keywords... keywords) {
        return Keywords.of(
                mergeSections(extractSections(Keywords::getUser, keywords), mapper),
                mergeSections(extractSections(Keywords::getSite, keywords), mapper));
    }

    public static Stream<ObjectNode> extractSections(Function<Keywords, ObjectNode> extractor, Keywords... keywords) {
        return Arrays.stream(keywords)
                .map(keyword -> clearObjectNode(ObjectUtil.getIfNotNull(keyword, extractor)))
                .filter(Objects::nonNull);
    }

    private static ObjectNode mergeSections(Stream<ObjectNode> sections, JacksonMapper mapper) {
        return sections.reduce(
                mapper.mapper().createObjectNode(),
                (left, right) -> (ObjectNode) mergeSections(left, right));
    }

    public static JsonNode mergeSections(JsonNode mainNode, JsonNode updateNode) {
        final Iterator<String> updateFieldNames = updateNode.fieldNames();
        while (updateFieldNames.hasNext()) {
            final String updateFieldName = updateFieldNames.next();
            final JsonNode valueToBeUpdated = mainNode.get(updateFieldName);
            final JsonNode updateValue = updateNode.get(updateFieldName);

            if (valueToBeUpdated != null && valueToBeUpdated.isArray() && updateValue.isArray()) {
                final ArrayNode arrayToBeUpdated = (ArrayNode) valueToBeUpdated;
                for (JsonNode updateChildNode : updateValue) {
                    arrayToBeUpdated.add(updateChildNode);
                }
            } else if (valueToBeUpdated != null && valueToBeUpdated.isObject()) {
                mergeSections(valueToBeUpdated, updateValue);
            } else if (mainNode.isObject()) {
                ((ObjectNode) mainNode).replace(updateFieldName, updateValue);
            }
        }
        return mainNode;
    }

    private static Map<String, JsonNode> jsonNodeToMap(JsonNode jsonNode, JacksonMapper mapper) {
        try {
            return jsonNode != null && jsonNode.isObject()
                    ? mapper.mapper().convertValue(jsonNode, MAP_TYPE_REF)
                    : Collections.emptyMap();
        } catch (IllegalArgumentException ignored) {
            return Collections.emptyMap();
        }
    }

    private static ObjectNode clearObjectNode(ObjectNode objectNode) {
        return objectNode != null && !objectNode.isEmpty() ? objectNode : null;
    }
}
