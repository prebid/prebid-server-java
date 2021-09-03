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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    public static Keywords resolveKeywordsFromOpenRtb(String userKeywords, String siteKeywords, JacksonMapper mapper) {
        return Keywords.of(
                resolveKeywordsSectionFromOpenRtb(userKeywords, "user", mapper),
                resolveKeywordsSectionFromOpenRtb(siteKeywords, "site", mapper));
    }

    public static ObjectNode resolveKeywordsSectionFromOpenRtb(String keywords, String section, JacksonMapper mapper) {
        final List<KeywordSegment> segments = Arrays.stream(keywords.split(","))
                .filter(StringUtils::isNotEmpty)
                .map(keyword -> KeywordSegment.of("keywords", keyword))
                .collect(Collectors.toList());

        final ObjectNode sectionNode = mapper.mapper().createObjectNode();

        if (!segments.isEmpty()) {
            final ObjectNode publisherNode = mapper.mapper().createObjectNode();
            final List<KeywordsPublisherItem> publisherItems = Collections.singletonList(
                    KeywordsPublisherItem.of("keywords", segments));
            publisherNode.set("ortb2", mapper.mapper().valueToTree(publisherItems));
            sectionNode.set(section, publisherNode);
        }

        return sectionNode;
    }

    public static Keywords resolveKeywordsFromExtGridKeywords(Keywords keywords, JacksonMapper mapper) {
        if (keywords == null) {
            return Keywords.empty();
        }

        final ObjectNode userSection = keywords.getUser();
        final ObjectNode resolvedUserSection = userSection != null
                                               ? resolveKeywordsSection(userSection, mapper)
                                               : null;
        final ObjectNode siteSection = keywords.getSite();
        final ObjectNode resolvedSiteSection = siteSection != null
                                               ? resolveKeywordsSection(siteSection, mapper)
                                               : null;

        return Keywords.of(resolvedUserSection, resolvedSiteSection);
    }

    public static ObjectNode resolveKeywordsSection(ObjectNode sectionNode, JacksonMapper mapper) {
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
        for (Iterator<JsonNode> it = publisherNode.elements(); it.hasNext(); ) {
            JsonNode publisherValueNode = it.next();
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

        for (Iterator<JsonNode> it = segmentsNode.elements(); it.hasNext(); ) {
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
        final List<KeywordSegment> keywordSegments = new ArrayList<>();
        for (Map.Entry<String, JsonNode> entry : jsonNodeToMap(publisherValueNode, mapper).entrySet()) {
            final JsonNode entryNode = entry.getValue();
            if (entryNode.isArray()) {
                keywordSegments.addAll(resolveAlternativePublisherSegmentsArray(entry.getKey(), entryNode));
            }
        }
        return keywordSegments;
    }

    public static List<KeywordSegment> resolveAlternativePublisherSegmentsArray(String segmentName,
                                                                                JsonNode publisherSegmentsNode) {
        final List<KeywordSegment> keywordSegments = new ArrayList<>();
        for (Iterator<JsonNode> it = publisherSegmentsNode.elements(); it.hasNext(); ) {
            final JsonNode currentNode = it.next();
            if (currentNode.isTextual()) {
                keywordSegments.add(KeywordSegment.of(segmentName, currentNode.asText()));
            }
        }
        return keywordSegments;
    }

    public static Keywords merge(JacksonMapper mapper, Keywords... keywords) {
        return Keywords.of(
                mergeSections(extractSections(Keywords::getUser, keywords), mapper),
                mergeSections(extractSections(Keywords::getSite, keywords), mapper));
    }

    public static Stream<ObjectNode> extractSections(Function<Keywords, ObjectNode> extractor, Keywords... keywords) {
        return Arrays.stream(keywords)
                .map(extGridKeyword -> extractSection(extractor, extGridKeyword))
                .filter(Objects::nonNull);
    }

    private static ObjectNode extractSection(Function<Keywords, ObjectNode> sectionExtractor,
                                             Keywords keywords) {
        final ObjectNode sectionNode = keywords != null ? sectionExtractor.apply(keywords) : null;
        return sectionNode != null && !sectionNode.isEmpty() ? sectionNode : null;
    }

    private static ObjectNode mergeSections(Stream<ObjectNode> sections, JacksonMapper mapper) {
        return sections.reduce(
                mapper.mapper().createObjectNode(),
                (left, right) -> (ObjectNode) mergeSections(left, right));
    }

    public static JsonNode mergeSections(JsonNode mainNode, JsonNode updateNode) {
        Iterator<String> updateFieldNames = updateNode.fieldNames();
        while (updateFieldNames.hasNext()) {
            String updateFieldName = updateFieldNames.next();
            JsonNode valueToBeUpdated = mainNode.get(updateFieldName);
            JsonNode updateValue = updateNode.get(updateFieldName);

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
        }
        return Collections.emptyMap();
    }

}
