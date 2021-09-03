package org.prebid.server.bidder.grid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.grid.model.ExtImpGrid;
import org.prebid.server.bidder.grid.model.ExtImpGridBidder;
import org.prebid.server.bidder.grid.model.ExtImpGridData;
import org.prebid.server.bidder.grid.model.ExtImpGridDataAdServer;
import org.prebid.server.bidder.grid.model.KeywordSegment;
import org.prebid.server.bidder.grid.model.Keywords;
import org.prebid.server.bidder.grid.model.KeywordsPublisherItem;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

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

public class GridBidder implements Bidder<BidRequest> {

    private static final TypeReference<Map<String, JsonNode>> MAP_TYPE_REF =
            new TypeReference<Map<String, JsonNode>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public GridBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> imps = request.getImp();
        final List<Imp> modifiedImps = modifyImps(imps, errors);

        if (modifiedImps.isEmpty()) {
            errors.add(BidderError.badInput("No valid impressions for grid"));
            return Result.withErrors(errors);
        }

        final Keywords firstImpKeywords = getKeywordsFromImpExt(imps.get(0).getExt());
        final BidRequest modifiedRequest = modifyRequest(request, firstImpKeywords, modifiedImps);
        return Result.of(Collections.singletonList(constructHttpRequest(modifiedRequest)), errors);
    }

    private List<Imp> modifyImps(List<Imp> imps, List<BidderError> errors) {
        final List<Imp> modifiedImps = new ArrayList<>();
        for (Imp imp : imps) {
            try {
                final ExtImpGrid extImpGrid = mapper.mapper().convertValue(imp.getExt(), ExtImpGrid.class);
                modifiedImps.add(modifyImp(imp, extImpGrid));
            } catch (IllegalArgumentException | PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        return modifiedImps;
    }

    private Imp modifyImp(Imp imp, ExtImpGrid extImpGrid) {
        final ExtImpGridBidder extImpGridBidder = extImpGrid != null ? extImpGrid.getBidder() : null;
        final Integer uid = extImpGridBidder != null ? extImpGridBidder.getUid() : null;
        if (uid == null || uid == 0) {
            throw new PreBidException("uid is empty");
        }

        final ExtImpGridData extImpData = extImpGrid.getData();
        final ExtImpGridDataAdServer adServer = extImpData != null ? extImpData.getAdServer() : null;
        final String adSlot = adServer != null ? adServer.getAdSlot() : null;

        if (StringUtils.isNotEmpty(adSlot)) {
            final ExtImpGrid modifiedExtImpGrid = extImpGrid.toBuilder()
                    .gpid(adSlot)
                    .build();
            return imp.toBuilder()
                    .ext(mapper.mapper().valueToTree(modifiedExtImpGrid))
                    .build();
        }
        return imp;
    }

    private Keywords getKeywordsFromImpExt(JsonNode extImp) {
        try {
            final ExtImpGrid firstImpExtGrid = mapper.mapper().convertValue(extImp, ExtImpGrid.class);
            final ExtImpGridBidder firstImpExtGridBidder = firstImpExtGrid != null
                    ? firstImpExtGrid.getBidder()
                    : null;
            return firstImpExtGridBidder != null ? firstImpExtGridBidder.getKeywords() : null;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private BidRequest modifyRequest(BidRequest bidRequest, Keywords firstImpKeywords, List<Imp> imp) {
        final User user = bidRequest.getUser();
        final String userKeywords = user != null ? user.getKeywords() : null;
        final Site site = bidRequest.getSite();
        final String siteKeywords = site != null ? site.getKeywords() : null;

        final ExtRequest extRequest = bidRequest.getExt();
        final Keywords resolvedKeywords = buildBidRequestExtKeywords(
                ObjectUtils.defaultIfNull(userKeywords, ""),
                ObjectUtils.defaultIfNull(siteKeywords, ""),
                firstImpKeywords,
                getKeywordsFromRequestExt(extRequest));

        return bidRequest.toBuilder()
                .imp(imp)
                .ext(modifyExtRequest(extRequest, resolvedKeywords))
                .build();
    }

    private ExtRequest modifyExtRequest(ExtRequest extRequest, Keywords keywords) {
        final ExtRequestPrebid extRequestPrebid = extRequest != null ? extRequest.getPrebid() : null;
        final Map<String, JsonNode> extRequestProperties = extRequest != null
                ? extRequest.getProperties()
                : Collections.emptyMap();
        final Map<String, JsonNode> modifiedExtRequestProperties = new HashMap<>(extRequestProperties);

        final ObjectNode clearedUserNode = clearObjectNode(keywords.getUser());
        final ObjectNode clearedSiteNode = clearObjectNode(keywords.getSite());
        if (clearedUserNode != null || clearedSiteNode != null) {
            final Keywords clearedKeywords = Keywords.of(clearedUserNode, clearedSiteNode);
            modifiedExtRequestProperties.put("keywords", mapper.mapper().valueToTree(clearedKeywords));
        } else {
            modifiedExtRequestProperties.remove("keywords");
        }

        if (!modifiedExtRequestProperties.isEmpty()) {
            final ExtRequest modifiedBidRequestExt = ExtRequest.of(extRequestPrebid);
            modifiedBidRequestExt.addProperties(modifiedExtRequestProperties);
            return modifiedBidRequestExt;
        }
        return null;
    }

    private ObjectNode clearObjectNode(ObjectNode objectNode) {
        return objectNode != null && !objectNode.isEmpty() ? objectNode : null;
    }

    private Keywords getKeywordsFromRequestExt(ExtRequest extRequest) {
        try {
            final JsonNode requestKeywordsNode = extRequest != null ? extRequest.getProperty("keywords") : null;
            return requestKeywordsNode != null
                    ? mapper.mapper().treeToValue(requestKeywordsNode, Keywords.class)
                    : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private Keywords buildBidRequestExtKeywords(String userKeywords,
                                                String siteKeywords,
                                                Keywords firstImpExtKeywords,
                                                Keywords requestExtKeywords) {
        return merge(
                resolveKeywordsFromOpenRtb(userKeywords, siteKeywords),
                resolveKeywordsFromExtGridKeywords(firstImpExtKeywords),
                resolveKeywordsFromExtGridKeywords(requestExtKeywords));
    }

    private Keywords resolveKeywordsFromOpenRtb(String userKeywords, String siteKeywords) {
        return Keywords.of(
                resolveKeywordsSectionFromOpenRtb(userKeywords),
                resolveKeywordsSectionFromOpenRtb(siteKeywords));
    }

    private ObjectNode resolveKeywordsSectionFromOpenRtb(String keywords) {
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

    private Keywords resolveKeywordsFromExtGridKeywords(Keywords keywords) {
        if (keywords == null) {
            return Keywords.empty();
        }

        final ObjectNode userSection = keywords.getUser();
        final ObjectNode resolvedUserSection = userSection != null
                ? resolveKeywordsSection(userSection)
                : null;
        final ObjectNode siteSection = keywords.getSite();
        final ObjectNode resolvedSiteSection = siteSection != null
                ? resolveKeywordsSection(siteSection)
                : null;

        return Keywords.of(resolvedUserSection, resolvedSiteSection);
    }

    private ObjectNode resolveKeywordsSection(ObjectNode sectionNode) {
        final ObjectNode resolvedSectionNode = mapper.mapper().createObjectNode();
        final Map<String, JsonNode> sectionMap = jsonNodeToMap(sectionNode);

        for (Map.Entry<String, JsonNode> entry : sectionMap.entrySet()) {
            JsonNode publisherJsonNode = entry.getValue();
            if (publisherJsonNode != null && publisherJsonNode.isArray()) {
                final List<KeywordsPublisherItem> publisherKeywords = resolvePublisherKeywords(publisherJsonNode);
                if (!publisherKeywords.isEmpty()) {
                    resolvedSectionNode.set(entry.getKey(), mapper.mapper().valueToTree(publisherKeywords));
                }
            }
        }
        return resolvedSectionNode;
    }

    private List<KeywordsPublisherItem> resolvePublisherKeywords(JsonNode publisherNode) {
        final List<KeywordsPublisherItem> publishersKeywords = new ArrayList<>();
        for (Iterator<JsonNode> it = publisherNode.elements(); it.hasNext();) {
            JsonNode publisherValueNode = it.next();
            final JsonNode publisherNameNode = publisherValueNode.get("name");
            final JsonNode segmentsNode = publisherValueNode.get("segments");

            if (publisherNameNode != null && publisherNameNode.isTextual()) {
                final List<KeywordSegment> segments = new ArrayList<>(resolvePublisherSegments(segmentsNode));
                segments.addAll(resolveAlternativePublisherSegments(publisherValueNode));

                if (!segments.isEmpty()) {
                    publishersKeywords.add(KeywordsPublisherItem.of(publisherNameNode.asText(), segments));
                }
            }
        }
        return publishersKeywords;
    }

    private List<KeywordSegment> resolvePublisherSegments(JsonNode segmentsNode) {
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

    private KeywordSegment resolvePublisherSegment(JsonNode segmentNode) {
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
        for (Map.Entry<String, JsonNode> entry : jsonNodeToMap(publisherValueNode).entrySet()) {
            final JsonNode entryNode = entry.getValue();
            if (entryNode.isArray()) {
                keywordSegments.addAll(resolveAlternativePublisherSegmentsArray(entry.getKey(), entryNode));
            }
        }
        return keywordSegments;
    }

    private List<KeywordSegment> resolveAlternativePublisherSegmentsArray(String segmentName,
                                                                          JsonNode publisherSegmentsNode) {
        final List<KeywordSegment> keywordSegments = new ArrayList<>();
        for (Iterator<JsonNode> it = publisherSegmentsNode.elements(); it.hasNext();) {
            final JsonNode currentNode = it.next();
            if (currentNode.isTextual()) {
                keywordSegments.add(KeywordSegment.of(segmentName, currentNode.asText()));
            }
        }
        return keywordSegments;
    }

    private Keywords merge(Keywords... extGridsKeywords) {
        return Keywords.of(
                mergeSections(extractSections(Keywords::getUser, extGridsKeywords)),
                mergeSections(extractSections(Keywords::getSite, extGridsKeywords)));
    }

    private static Stream<ObjectNode> extractSections(Function<Keywords, ObjectNode> sectionExtractor,
                                                      Keywords... extGridsKeywords) {
        return Arrays.stream(extGridsKeywords)
                .map(extGridKeyword -> extractSection(sectionExtractor, extGridKeyword))
                .filter(Objects::nonNull);
    }

    private static ObjectNode extractSection(Function<Keywords, ObjectNode> sectionExtractor,
                                             Keywords keywords) {
        final ObjectNode sectionNode = keywords != null ? sectionExtractor.apply(keywords) : null;
        return sectionNode != null && !sectionNode.isEmpty() ? sectionNode : null;
    }

    private ObjectNode mergeSections(Stream<ObjectNode> sections) {
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
            } else if (mainNode instanceof ObjectNode) {
                ((ObjectNode) mainNode).replace(updateFieldName, updateValue);
            }
        }
        return mainNode;
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

    private HttpRequest<BidRequest> constructHttpRequest(BidRequest bidRequest) {
        return HttpRequest.<BidRequest>builder()
                .uri(endpointUrl)
                .method(HttpMethod.POST)
                .headers(HttpUtil.headers())
                .payload(bidRequest)
                .body(mapper.encode(bidRequest))
                .build();
    }

    @Override
    public final Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(httpCall.getRequest().getPayload(), bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }
        return bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid,
                        getBidMediaType(bid.getImpid(), bidRequest.getImp()), bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static BidType getBidMediaType(String impId, List<Imp> imps) {
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
