package org.prebid.server.bidder.grid;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.Bid;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.OpenrtbBidder;
import org.prebid.server.bidder.grid.model.ExtImpGrid;
import org.prebid.server.bidder.grid.model.GridExtImp;
import org.prebid.server.bidder.grid.model.GridExtImpData;
import org.prebid.server.bidder.grid.model.GridExtImpDataAdServer;
import org.prebid.server.bidder.grid.model.KeywordSegment;
import org.prebid.server.bidder.grid.model.KeywordsPublisherItem;
import org.prebid.server.bidder.model.ImpWithExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.json.JsonMerger;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
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

    private final JsonMerger jsonMerger;

    public GridBidder(String endpointUrl, JacksonMapper mapper) {
        super(endpointUrl, RequestCreationStrategy.SINGLE_REQUEST, ExtImpGrid.class, mapper);
        jsonMerger = new JsonMerger(mapper);
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

    @Override
    protected void modifyRequest(BidRequest bidRequest, BidRequest.BidRequestBuilder requestBuilder, List<ImpWithExt<ExtImpGrid>> impsWithExts) {
        final User user = bidRequest.getUser();
        final String userKeywords = user != null ? user.getKeywords() : null;
        final Site site = bidRequest.getSite();
        final String siteKeywords = site != null ? site.getKeywords() : null;

        final ExtRequest requestExt = bidRequest.getExt();
        final JsonNode requestExtNode = mapper.mapper().valueToTree(requestExt.getProperties());
        final ObjectNode constructedRequest = buildReqExt(
                ObjectUtils.defaultIfNull(userKeywords, ""),
                ObjectUtils.defaultIfNull(siteKeywords, ""),
                impsWithExts.get(0).getImp().getExt(),
                requestExtNode);


        requestBuilder.ext();
    }

    private ObjectNode buildReqExt(String userKeywords,
                                   String siteKeywords,
                                   JsonNode firstImpExt,
                                   JsonNode requestExt) {
        final JsonNode node1 = resolveKeywords(requestExt.get("keywords"));
        final JsonNode node2 = resolveKeywordsFromOpenRtb(userKeywords, "user");
        final JsonNode node3 = resolveKeywordsFromOpenRtb(siteKeywords, "site");
        final JsonNode node4 = resolveKeywords(firstImpExt.get("keywords"));
        return merge(
                resolveKeywords(requestExt.get("keywords")),
                resolveKeywordsFromOpenRtb(userKeywords, "user"),
                resolveKeywordsFromOpenRtb(siteKeywords, "site"),
                resolveKeywords(firstImpExt.get("keywords")));
    }

//    public static void main(String[] args) throws JsonProcessingException {
//
//        JacksonMapper mapper = new JacksonMapper(ObjectMapperProvider.mapper());
//
//        GridBidder gridBidder = new GridBidder("http://endpoint.com", mapper);
//        final ObjectNode requestExt = (ObjectNode) mapper.mapper().readTree(requestExtJson);
//        final ObjectNode firstImpExt = (ObjectNode) mapper.mapper().readTree(firstImpjson);
//        final JsonNode result = gridBidder.buildReqExt(userKeywords, siteKeywords, firstImpExt, requestExt);
//        System.out.println(result.toPrettyString());
//        final ObjectNode node1 = mapper.mapper().createObjectNode();
//        final ArrayNode arrayNode1 = mapper.mapper().createArrayNode();
//        arrayNode1.addAll(Arrays.asList(TextNode.valueOf("rabotai pj")));
//        node1.set("test", arrayNode1);
//
//        final ObjectNode node2 = mapper.mapper().createObjectNode();
//        final ArrayNode arrayNode2 = mapper.mapper().createArrayNode();
//        arrayNode2.addAll(Arrays.asList(TextNode.valueOf("nu pojaluysta")));
//        node2.set("test", arrayNode2);
//        final ObjectNode result = gridBidder.merge(node1, node2);
//    }

    private ObjectNode resolveKeywordsFromOpenRtb(String keywords, String section) {
        final List<KeywordSegment> segments = Arrays.stream(keywords.split(","))
                .filter(StringUtils::isNotEmpty)
                .map(keyword -> KeywordSegment.of("keywords", keyword))
                .collect(Collectors.toList());

        final ObjectNode keywordsNode = mapper.mapper().createObjectNode();

        if (!segments.isEmpty()) {
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

            final List<KeywordsPublisherItem> publisherKeywords =
                    resolvePublisherKeywords((ArrayNode) publisherJsonNode);
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

            if (publisherNameNode == null || !publisherNameNode.isTextual()) {
                continue;
            }

            final List<KeywordSegment> segments = new ArrayList<>();
            if (segmentsNode != null && segmentsNode.isArray()) {
                segments.addAll(resolvePublisherSegments((ArrayNode) segmentsNode));
            }
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

    private ObjectNode merge(JsonNode... nodes) {
        return (ObjectNode) Arrays.stream(nodes)
                .reduce(mapper.mapper().createObjectNode(), jsonMerger::merge);
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

    public static final String requestExtJson = "{\n" +
            "  \"keywords\": {\n" +
            "    \"stringKey\": \"stringVal\",\n" +
            "    \"wrongKeys1\": {\n" +
            "      \"someKey1\": \"someVal1\",\n" +
            "      \"someKey2\": \"someVal2\",\n" +
            "      \"someKey3\": [\n" +
            "        \"someVal31\",\n" +
            "        \"someVal32\"\n" +
            "      ],\n" +
            "      \"someKey4\": {\n" +
            "        \"key1\": \"val1\",\n" +
            "        \"key2\": \"val2\"\n" +
            "      }\n" +
            "    },\n" +
            "    \"anotherKeys\": [\n" +
            "      {\n" +
            "        \"someKey1\": \"someVal1\"\n" +
            "      },\n" +
            "      {\n" +
            "        \"someKey2\": \"someVal2\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"site\": {\n" +
            "      \"stringSiteKey\": \"stringSiteVal\",\n" +
            "      \"wrongSiteKeys1\": {\n" +
            "        \"someKey1\": \"someVal1\",\n" +
            "        \"someKey2\": \"someVal2\",\n" +
            "        \"someKey3\": [\n" +
            "          \"someVal31\",\n" +
            "          \"someVal32\"\n" +
            "        ],\n" +
            "        \"someKey4\": {\n" +
            "          \"key1\": \"val1\",\n" +
            "          \"key2\": \"val2\"\n" +
            "        }\n" +
            "      },\n" +
            "      \"anotherSiteKeys\": [\n" +
            "        {\n" +
            "          \"someKey1\": \"someVal1\"\n" +
            "        },\n" +
            "        {\n" +
            "          \"someKey2\": \"someVal2\"\n" +
            "        },\n" +
            "        \"someStrKey\",\n" +
            "        [\n" +
            "          \"someVal31\",\n" +
            "          \"someVal32\"\n" +
            "        ],\n" +
            "        {\n" +
            "          \"name\": \"someName3\",\n" +
            "          \"keyName\": [\n" +
            "            \"keyVal\",\n" +
            "            {\n" +
            "              \"name\": \"wrongKey\"\n" +
            "            }\n" +
            "          ],\n" +
            "          \"wrongKeyName\": \"stKeyVal\",\n" +
            "          \"wrongKeyName2\": {\n" +
            "            \"name\": \"someName\",\n" +
            "            \"value\": \"stKeyVal\"\n" +
            "          }\n" +
            "        }\n" +
            "      ],\n" +
            "      \"pub\": [\n" +
            "        \"k1\",\n" +
            "        \"k2\"\n" +
            "      ],\n" +
            "      \"somePublisher\": [\n" +
            "        {\n" +
            "          \"name\": \"someName2\",\n" +
            "          \"topic\": [\n" +
            "            \"anyKey\"\n" +
            "          ]\n" +
            "        }\n" +
            "      ],\n" +
            "      \"formatedSitePublisher\": [\n" +
            "        {\n" +
            "          \"name\": \"formatedPub2\",\n" +
            "          \"segments\": [\n" +
            "            {\n" +
            "              \"name\": \"segName1\",\n" +
            "              \"value\": \"segVal1\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"segName2\",\n" +
            "              \"value\": \"segVal2\"\n" +
            "            }\n" +
            "          ]\n" +
            "        },\n" +
            "        {\n" +
            "          \"name\": \"notFormatedPub\",\n" +
            "          \"topic2\": [\n" +
            "            \"notFormatedKw\"\n" +
            "          ]\n" +
            "        }\n" +
            "      ]\n" +
            "    },\n" +
            "    \"user\": {\n" +
            "      \"formatedUserPublisher\": [\n" +
            "        {\n" +
            "          \"name\": \"formatedPub2\",\n" +
            "          \"segments\": [\n" +
            "            {\n" +
            "              \"name\": \"segName1\",\n" +
            "              \"value\": \"segVal1\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"segName2\",\n" +
            "              \"value\": \"segVal2\"\n" +
            "            }\n" +
            "          ]\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  }\n" +
            "}";

    public static final String firstImpjson = "{\n" +
            "  \"uid\": 1,\n" +
            "  \"keywords\": {\n" +
            "    \"site\": {\n" +
            "      \"somePublisher\": [\n" +
            "        {\n" +
            "          \"name\": \"someName\",\n" +
            "          \"topic\": [\n" +
            "            \"stress\",\n" +
            "            \"fear\"\n" +
            "          ]\n" +
            "        }\n" +
            "      ]\n" +
            "    },\n" +
            "    \"user\": {\n" +
            "      \"formatedPublisher\": [\n" +
            "        {\n" +
            "          \"name\": \"formatedPub1\",\n" +
            "          \"segments\": [\n" +
            "            {\n" +
            "              \"name\": \"segName1\",\n" +
            "              \"value\": \"segVal1\"\n" +
            "            },\n" +
            "            {\n" +
            "              \"name\": \"segName2\",\n" +
            "              \"value\": \"segVal2\"\n" +
            "            },\n" +
            "            [\n" +
            "              \"someKeyword\"\n" +
            "            ],\n" +
            "            \"stringKey\"\n" +
            "          ],\n" +
            "          \"bottom\": [\n" +
            "            \"bottomKey1\",\n" +
            "            \"bottomKey2\"\n" +
            "          ]\n" +
            "        }\n" +
            "      ]\n" +
            "    }\n" +
            "  }\n" +
            "}";

    public static final String userKeywords = "userKey1";
    public static final String siteKeywords = "siteKey1,siteKey2";
}
