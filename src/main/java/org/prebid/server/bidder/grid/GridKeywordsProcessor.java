package org.prebid.server.bidder.grid;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.bidder.grid.model.KeywordSegment;
import org.prebid.server.bidder.grid.model.Keywords;
import org.prebid.server.bidder.grid.model.KeywordsPublisherItem;
import org.prebid.server.util.ObjectUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GridKeywordsProcessor {

    private static final TypeReference<Map<String, JsonNode>> MAP_TYPE_REF =
            new TypeReference<>() {
            };
    private static final String KEYWORDS_PROPERTY = "keywords";
    private static final String USER_PROPERTY = "user";
    private static final String SITE_PROPERTY = "site";

    private final JacksonMapper mapper;

    public GridKeywordsProcessor(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    public Map<String, JsonNode> modifyWithKeywords(Map<String, JsonNode> extRequestProperties,
                                                    Keywords keywords) {

        final JsonNode keywordsJsonNode = extRequestProperties.get(KEYWORDS_PROPERTY);
        final ObjectNode keywordsNode = isObjectNode(keywordsJsonNode)
                ? (ObjectNode) keywordsJsonNode
                : mapper.mapper().createObjectNode();

        setIfNotNullOrRemove(keywordsNode, USER_PROPERTY, stripToNull(keywords.getUser()));
        setIfNotNullOrRemove(keywordsNode, SITE_PROPERTY, stripToNull(keywords.getSite()));

        final Map<String, JsonNode> modifiedExtRequestProperties = new HashMap<>(extRequestProperties);
        if (!keywordsNode.isEmpty()) {
            modifiedExtRequestProperties.put(KEYWORDS_PROPERTY, keywordsNode);
        } else {
            modifiedExtRequestProperties.remove(KEYWORDS_PROPERTY);
        }
        return modifiedExtRequestProperties;
    }

    private static void setIfNotNullOrRemove(ObjectNode node, String key, JsonNode value) {
        if (value != null) {
            node.set(key, value);
        } else {
            node.remove(key);
        }
    }

    public Keywords resolveKeywordsFromOpenRtb(String userKeywords, String siteKeywords) {
        return Keywords.of(
                resolveKeywordsSectionFromOpenRtb(userKeywords),
                resolveKeywordsSectionFromOpenRtb(siteKeywords));
    }

    public ObjectNode resolveKeywordsSectionFromOpenRtb(String keywords) {
        final List<KeywordSegment> segments = Arrays.stream(keywords.split(","))
                .filter(StringUtils::isNotEmpty)
                .map(keyword -> KeywordSegment.of(KEYWORDS_PROPERTY, keyword))
                .collect(Collectors.toList());

        final ObjectNode publisherNode = mapper.mapper().createObjectNode();
        if (!segments.isEmpty()) {
            final List<KeywordsPublisherItem> publisherItems = Collections.singletonList(
                    KeywordsPublisherItem.of(KEYWORDS_PROPERTY, segments));
            return publisherNode.set("ortb2", mapper.mapper().valueToTree(publisherItems));
        }
        return publisherNode;
    }

    public Keywords resolveKeywords(Keywords keywords) {
        return keywords == null
                ? Keywords.empty()
                : Keywords.of(
                resolveKeywordsSection(keywords.getUser()),
                resolveKeywordsSection(keywords.getSite()));
    }

    public ObjectNode resolveKeywordsSection(ObjectNode sectionNode) {
        if (sectionNode == null) {
            return null;
        }

        final ObjectNode resolvedSectionNode = mapper.mapper().createObjectNode();
        final Map<String, JsonNode> sectionMap = jsonNodeToMap(sectionNode);

        for (Map.Entry<String, JsonNode> entry : sectionMap.entrySet()) {
            final JsonNode publisherJsonNode = entry.getValue();
            if (isArrayNode(publisherJsonNode)) {
                final List<KeywordsPublisherItem> publisherKeywords = resolvePublisherKeywords(publisherJsonNode);
                if (!publisherKeywords.isEmpty()) {
                    resolvedSectionNode.set(entry.getKey(), mapper.mapper().valueToTree(publisherKeywords));
                }
            }
        }
        return resolvedSectionNode;
    }

    public List<KeywordsPublisherItem> resolvePublisherKeywords(JsonNode publisherNode) {
        final List<KeywordsPublisherItem> publishersKeywords = new ArrayList<>();
        final Iterator<JsonNode> publisherNodeElements = publisherNode.elements();

        while (publisherNodeElements.hasNext()) {
            final JsonNode publisherValueNode = publisherNodeElements.next();
            final JsonNode publisherNameNode = publisherValueNode.get("name");
            final JsonNode segmentsNode = publisherValueNode.get("segments");

            if (isTextualNode(publisherNameNode)) {
                final List<KeywordSegment> segments = new ArrayList<>(resolvePublisherSegments(segmentsNode));
                segments.addAll(resolveAlternativePublisherSegments(publisherValueNode));

                if (!segments.isEmpty()) {
                    publishersKeywords.add(KeywordsPublisherItem.of(publisherNameNode.asText(), segments));
                }
            }
        }
        return publishersKeywords;
    }

    public static List<KeywordSegment> resolvePublisherSegments(JsonNode segmentsNode) {
        final List<KeywordSegment> parsedSegments = new ArrayList<>();
        if (!isArrayNode(segmentsNode)) {
            return parsedSegments;
        }

        final Iterator<JsonNode> segments = segmentsNode.elements();
        while (segments.hasNext()) {
            final KeywordSegment keywordSegment = resolvePublisherSegment(segments.next());
            if (keywordSegment != null) {
                parsedSegments.add(keywordSegment);
            }
        }
        return parsedSegments;
    }

    public static KeywordSegment resolvePublisherSegment(JsonNode segmentNode) {
        final JsonNode nameNode = segmentNode.get("name");
        final String name = isTextualNode(nameNode) ? nameNode.asText() : null;
        final JsonNode valueNode = segmentNode.get("value");
        final String value = isTextualNode(valueNode) ? valueNode.asText() : null;

        return StringUtils.isNotEmpty(name) && StringUtils.isNotEmpty(value)
                ? KeywordSegment.of(name, value)
                : null;
    }

    public List<KeywordSegment> resolveAlternativePublisherSegments(JsonNode publisherValueNode) {
        return jsonNodeToMap(publisherValueNode).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(publisherEntry -> isArrayNode(publisherEntry.getValue()))
                .map(GridKeywordsProcessor::mapPublisherEntryToKeywordSegmentList)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private static List<KeywordSegment> mapPublisherEntryToKeywordSegmentList(
            Map.Entry<String, JsonNode> publisherEntry) {

        final List<KeywordSegment> keywordSegments = new ArrayList<>();
        final Iterator<JsonNode> publisherEntryElements = publisherEntry.getValue().elements();

        while (publisherEntryElements.hasNext()) {
            final JsonNode currentNode = publisherEntryElements.next();
            if (currentNode.isTextual()) {
                keywordSegments.add(KeywordSegment.of(publisherEntry.getKey(), currentNode.asText()));
            }
        }
        return keywordSegments;
    }

    public Keywords merge(Keywords... keywords) {
        return Keywords.of(
                mergeSections(extractSections(Keywords::getUser, keywords)),
                mergeSections(extractSections(Keywords::getSite, keywords)));
    }

    public Stream<ObjectNode> extractSections(Function<Keywords, ObjectNode> extractor, Keywords... keywords) {
        return Arrays.stream(keywords)
                .map(keyword -> stripToNull(ObjectUtil.getIfNotNull(keyword, extractor)))
                .filter(Objects::nonNull);
    }

    private ObjectNode mergeSections(Stream<ObjectNode> sections) {
        return sections.reduce(
                mapper.mapper().createObjectNode(),
                (left, right) -> (ObjectNode) mergeSections(left, right));
    }

    public static JsonNode mergeSections(JsonNode firstSection, JsonNode secondSection) {
        final Iterator<String> updateFieldNames = secondSection.fieldNames();
        while (updateFieldNames.hasNext()) {
            final String updateFieldName = updateFieldNames.next();
            final JsonNode valueToBeUpdated = firstSection.get(updateFieldName);
            final JsonNode updateValue = secondSection.get(updateFieldName);

            if (isArrayNode(valueToBeUpdated) && isArrayNode(updateValue)) {
                final ArrayNode arrayToBeUpdated = (ArrayNode) valueToBeUpdated;
                for (JsonNode updateChildNode : updateValue) {
                    arrayToBeUpdated.add(updateChildNode);
                }
            } else if (isObjectNode(valueToBeUpdated)) {
                mergeSections(valueToBeUpdated, updateValue);
            } else if (isObjectNode(firstSection)) {
                ((ObjectNode) firstSection).replace(updateFieldName, updateValue);
            }
        }
        return firstSection;
    }

    private Map<String, JsonNode> jsonNodeToMap(JsonNode jsonNode) {
        try {
            return isObjectNode(jsonNode)
                    ? mapper.mapper().convertValue(jsonNode, MAP_TYPE_REF)
                    : Collections.emptyMap();
        } catch (IllegalArgumentException ignored) {
            return Collections.emptyMap();
        }
    }

    private static ObjectNode stripToNull(ObjectNode objectNode) {
        return objectNode != null && !objectNode.isEmpty() ? objectNode : null;
    }

    private static boolean isArrayNode(JsonNode jsonNode) {
        return jsonNode != null && jsonNode.isArray();
    }

    private static boolean isObjectNode(JsonNode jsonNode) {
        return jsonNode != null && jsonNode.isObject();
    }

    private static boolean isTextualNode(JsonNode jsonNode) {
        return jsonNode != null && jsonNode.isTextual();
    }
}
