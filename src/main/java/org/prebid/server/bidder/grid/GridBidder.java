package org.prebid.server.bidder.grid;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.bidder.grid.model.ExtImpGrid;
import org.prebid.server.bidder.grid.model.GridExtImp;
import org.prebid.server.bidder.grid.model.GridExtImpData;
import org.prebid.server.bidder.grid.model.GridExtImpDataAdServer;
import org.prebid.server.bidder.grid.model.KeywordSegment;
import org.prebid.server.bidder.grid.model.KeywordsPublisherItem;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.response.BidType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GridBidder extends OpenrtbBidder<ExtImpGrid> {

    public static final TypeReference<Map<String, JsonNode>> MAP_TYPE_REF =
            new TypeReference<Map<String, JsonNode>>() {
            };
    private final Set<String> ALLOWED_KEYWORDS_SECTIONS = Set.of("user", "site");

    public GridBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpGrid.class, mapper);
    }

    @Override
    protected Imp modifyImp(Imp imp, ExtImpGrid impExt) {
        if (impExt.getUid() == null || impExt.getUid() == 0) {
            throw new PreBidException("uid is empty");
        }

        final GridExtImp gridExtImp;
        try {
            gridExtImp = mapper.mapper().convertValue(imp.getExt(), GridExtImp.class);
        } catch (Exception e) {
            throw new PreBidException(e.getMessage());
        }

        final GridExtImpData extImpData = gridExtImp != null ? gridExtImp.getData() : null;
        final GridExtImpDataAdServer adServer = extImpData != null ? extImpData.getAdServer() : null;
        final String adSlot = adServer != null ? adServer.getAdSlot() : null;
        if (StringUtils.isNotEmpty(adSlot)) {

            final GridExtImp modifiedGridExtImp = gridExtImp.toBuilder()
                    .gpid(adSlot)
                    .build();

            return imp.toBuilder().ext(mapper.mapper().valueToTree(modifiedGridExtImp)).build();
        }

        return imp;
    }

    private ObjectNode buildReqExt(String userKeywords,
                                   String siteKeywords,
                                   ObjectNode firstImpExt,
                                   ObjectNode requestExt) {
        final ObjectNode resolvedOpenRtbUserKeywords = resolveKeywordsFromOpenRtb(userKeywords, "user");
        final ObjectNode resolvedOpenRtbSiteKeywords = resolveKeywordsFromOpenRtb(siteKeywords, "site");
        final ObjectNode resolvedRequestKeywordsNode = resolveKeywords(requestExt.at("keywords"));
        final ObjectNode resolvedFirstImpKeywordsNode = resolveKeywords(firstImpExt.get("keywords"));
        return null;
    }

    private ObjectNode resolveKeywordsFromOpenRtb(String keywords, String section) {
        final List<KeywordSegment> segments = Arrays.stream(keywords.split(","))
                .filter(StringUtils::isNotEmpty)
                .map(keyword -> KeywordSegment.of("keywords", keyword))
                .collect(Collectors.toList());

        final ObjectNode keywordsNode = mapper.mapper().createObjectNode();

        if (segments.isEmpty()) {
            final List<KeywordsPublisherItem> publisherItems = List.of(KeywordsPublisherItem.of("keywords", segments));
            final ObjectNode publisherNode = mapper.mapper().createObjectNode();
            publisherNode.set("ortb2", mapper.mapper().valueToTree(publisherItems));
            return keywordsNode.set(section, publisherNode);
        }
        return keywordsNode;
    }

    // parse keywords
    private ObjectNode resolveKeywords(JsonNode keywordsNode) {
        final ObjectNode resolvedKeywordsNode = mapper.mapper().createObjectNode();
        for (String section : ALLOWED_KEYWORDS_SECTIONS) {
            final JsonNode sectionNode = keywordsNode.get(section);
            if (sectionNode != null && sectionNode.isObject()) {
                final JsonNode resolvedUserKeywordsNode = resolveKeywordsFromSection(sectionNode);
                if (!resolvedUserKeywordsNode.isEmpty()) {
                    resolvedKeywordsNode.set(section, resolvedUserKeywordsNode);
                }
            }
        }
        return resolvedKeywordsNode;
    }

    // parse keywords.[user, site]
    private JsonNode resolveKeywordsFromSection(JsonNode sectionNode) {
        final ObjectNode resolvedSectionNode = mapper.mapper().createObjectNode();
        final Map<String, JsonNode> sectionMap = jsonNodeToMap(sectionNode);

        for (Map.Entry<String, JsonNode> entry : sectionMap.entrySet()) {
            JsonNode publisherJsonNode = entry.getValue();
            if (publisherJsonNode == null || !publisherJsonNode.isArray()) {
                continue;
            }

            final List<KeywordsPublisherItem> publisherKeywords = resolvePublisherKeywords((ArrayNode) publisherJsonNode);
            if (!publisherKeywords.isEmpty()) {
                resolvedSectionNode.set(entry.getKey(), mapper.mapper().valueToTree(publisherKeywords));
            }
        }
        return resolvedSectionNode;
    }

    // parse keywords.[user, site].{publisherName}
    private List<KeywordsPublisherItem> resolvePublisherKeywords(ArrayNode publisherNode) {
        final List<KeywordsPublisherItem> publishersKeywords = new ArrayList<>();
        for (Iterator<JsonNode> it = publisherNode.elements(); it.hasNext(); ) {
            JsonNode publisherValueJsonNode = it.next();
            if (!publisherValueJsonNode.isObject()) {
                continue;
            }
            final ObjectNode publisherValueNode = (ObjectNode) publisherValueJsonNode;
            final JsonNode publisherNameNode = publisherValueNode.get("name");
            final JsonNode segmentsNode = publisherValueNode.get("segments");

            if (publisherNameNode == null
                    || !publisherNameNode.isTextual()
                    || segmentsNode == null
                    || !segmentsNode.isArray()) {
                continue;
            }

            final List<KeywordSegment> segments = resolvePublisherSegments((ArrayNode) segmentsNode);
            segments.addAll(resolveAlternativePublisherSegments(publisherValueNode));
            if (!segments.isEmpty()) {
                publishersKeywords.add(KeywordsPublisherItem.of(publisherNameNode.asText(), segments));
            }
        }
        return publishersKeywords;
    }

    // parse keywords.[user, site].{publisherName}.segments
    private List<KeywordSegment> resolvePublisherSegments(ArrayNode segments) {
        final List<KeywordSegment> parsedSegments = new ArrayList<>();
        for (Iterator<JsonNode> it = segments.elements(); it.hasNext(); ) {
            final KeywordSegment keywordSegment = resolvePublisherKeywordSegment(it.next());
            if (keywordSegment != null) {
                parsedSegments.add(keywordSegment);
            }
        }

        return parsedSegments;
    }

    // parse keywords.[user, site].{publisherName}.segments[segmentIndex]
    private KeywordSegment resolvePublisherKeywordSegment(JsonNode segmentNode) {
        final JsonNode nameNode = segmentNode.get("name");
        final String name = nameNode != null && nameNode.isTextual() ? nameNode.asText() : null;
        final JsonNode valueNode = segmentNode.get("value");
        final String value = valueNode != null && valueNode.isTextual() ? valueNode.asText() : null;

        return StringUtils.isNotEmpty(name) && StringUtils.isNotEmpty(value)
                ? KeywordSegment.of(name, value)
                : null;
    }

    private List<KeywordSegment> resolveAlternativePublisherSegments(JsonNode publisherValueNode) {
        final List<KeywordSegment> keywordSegments = new ArrayList<>();
        Map<String, JsonNode> publisherValueNodes = jsonNodeToMap(publisherValueNode);

        for (Map.Entry<String, JsonNode> entry : publisherValueNodes.entrySet()) {
            final JsonNode jsonNode = entry.getValue();
            if (!jsonNode.isArray()) {
                continue;
            }

            final ArrayNode arrayNode = (ArrayNode) jsonNode;
            for (Iterator<JsonNode> it = arrayNode.elements(); it.hasNext(); ) {
                final JsonNode currentNode = it.next();

                if (!currentNode.isTextual()) {
                    continue;
                }
                keywordSegments.add(KeywordSegment.of(entry.getKey(), currentNode.asText()));
            }
        }

        return keywordSegments;
    }

    private Map<String, JsonNode> jsonNodeToMap(JsonNode jsonNode) {
        try {
            return jsonNode != null && jsonNode.isObject()
                    ? mapper.mapper().convertValue(jsonNode, MAP_TYPE_REF)
                    : Collections.emptyMap();
        } catch (IllegalArgumentException ignored) {
        }
        return Collections.emptyMap();
    }

    @Override
    protected BidType getBidType(Bid bid, List<Imp> imps) {
        final String impId = bid.getImpid();
        for (Imp imp : imps) {
            if (imp.getId().equals(impId)) {
                if (imp.getBanner() != null) {
                    return BidType.banner;
                }
                if (imp.getVideo() != null) {
                    return BidType.video;
                }
                throw new PreBidException(String.format("Unknown impression type for ID: %s", impId));
            }
        }
        throw new PreBidException(String.format("Failed to find impression for ID: %s", impId));
    }
}
