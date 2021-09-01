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
import com.iab.openrtb.response.Bid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.grid.model.ExtGridKeywords;
import org.prebid.server.bidder.grid.model.ExtImpGrid;
import org.prebid.server.bidder.grid.model.ExtImpGridBidder;
import org.prebid.server.bidder.grid.model.ExtImpGridData;
import org.prebid.server.bidder.grid.model.ExtImpGridDataAdServer;
import org.prebid.server.bidder.grid.model.KeywordSegment;
import org.prebid.server.bidder.grid.model.KeywordsPublisherItem;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

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

        try {
            final ExtImpGrid firstImpExtGrid = mapper.mapper().convertValue(imps.get(0).getExt(), ExtImpGrid.class);
            final ExtImpGridBidder firstImpExtGridBidder = firstImpExtGrid != null ? firstImpExtGrid.getBidder() : null;
            final ExtGridKeywords firstImpExtGridKeywords = firstImpExtGridBidder != null
                    ? firstImpExtGridBidder.getKeywords()
                    : null;
            return constructHttpRequest(modifyRequest(request, firstImpExtGridKeywords, modifiedImps));
        } catch (IllegalArgumentException e) {
            return constructHttpRequest(modifyRequest(request, null, modifiedImps));
        }
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

    protected Imp modifyImp(Imp imp, ExtImpGrid extImpGrid) {
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

    private Result<List<HttpRequest<BidRequest>>> constructHttpRequest(BidRequest bidRequest) {
        return Result.withValue(
                HttpRequest.<BidRequest>builder()
                        .uri(endpointUrl)
                        .method(HttpMethod.POST)
                        .headers(HttpUtil.headers())
                        .payload(bidRequest)
                        .body(mapper.encode(bidRequest))
                        .build());
    }

    protected BidRequest modifyRequest(BidRequest bidRequest, ExtGridKeywords firstImpExtGridKeywords, List<Imp> imp) {
        final User user = bidRequest.getUser();
        final String userKeywords = user != null ? user.getKeywords() : null;
        final Site site = bidRequest.getSite();
        final String siteKeywords = site != null ? site.getKeywords() : null;

        final ExtRequest extRequest = bidRequest.getExt();
        final ExtGridKeywords resolvedKeywords = buildBidRequestExtKeywords(
                ObjectUtils.defaultIfNull(userKeywords, ""),
                ObjectUtils.defaultIfNull(siteKeywords, ""),
                firstImpExtGridKeywords,
                getKeywordsFromRequestExt(extRequest));

        return bidRequest.toBuilder()
                .imp(imp)
                .ext(modifyExtRequest(extRequest, resolvedKeywords))
                .build();
    }

    private ExtRequest modifyExtRequest(ExtRequest extRequest, ExtGridKeywords extGridKeywords) {
        final Map<String, JsonNode> extRequestProperties = extRequest != null
                ? extRequest.getProperties()
                : Collections.emptyMap();

        final ExtRequest modifiedBidRequestExt = ExtRequest.of(extRequest != null ? extRequest.getPrebid() : null);
        modifiedBidRequestExt.addProperties(extRequestProperties);
        modifiedBidRequestExt.addProperty("keywords", mapper.mapper().valueToTree(extGridKeywords));
        return modifiedBidRequestExt;
    }

    private ExtGridKeywords getKeywordsFromRequestExt(ExtRequest extRequest) {
        try {
            final JsonNode requestKeywordsNode = extRequest != null ? extRequest.getProperty("keywords") : null;
            return requestKeywordsNode != null
                    ? mapper.mapper().treeToValue(requestKeywordsNode, ExtGridKeywords.class)
                    : null;
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private ExtGridKeywords buildBidRequestExtKeywords(String userKeywords,
                                                       String siteKeywords,
                                                       ExtGridKeywords firstImpExtKeywords,
                                                       ExtGridKeywords requestExtKeywords) {
        return merge(
                resolveKeywordsFromOpenRtb(userKeywords, siteKeywords),
                resolveKeywordsFromExtGridKeywords(firstImpExtKeywords),
                resolveKeywordsFromExtGridKeywords(requestExtKeywords));
    }

    private ExtGridKeywords resolveKeywordsFromOpenRtb(String userKeywords, String siteKeywords) {
        return ExtGridKeywords.of(
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
            final List<KeywordsPublisherItem> publisherItems = List.of(KeywordsPublisherItem.of("keywords", segments));
            return publisherNode.set("ortb2", mapper.mapper().valueToTree(publisherItems));
        }
        return publisherNode;
    }

    private ExtGridKeywords resolveKeywordsFromExtGridKeywords(ExtGridKeywords extGridKeywords) {
        if (extGridKeywords == null) {
            return ExtGridKeywords.empty();
        }

        final ObjectNode userSection = extGridKeywords.getUser();
        final ObjectNode resolvedUserSection = userSection != null
                ? resolveKeywordsSection(userSection)
                : null;
        final ObjectNode siteSection = extGridKeywords.getSite();
        final ObjectNode resolvedSiteSection = siteSection != null
                ? resolveKeywordsSection(siteSection)
                : null;

        return ExtGridKeywords.of(resolvedUserSection, resolvedSiteSection);
    }

    private ObjectNode resolveKeywordsSection(ObjectNode sectionNode) {
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

    private List<KeywordSegment> resolvePublisherSegments(ArrayNode segments) {
        final List<KeywordSegment> parsedSegments = new ArrayList<>();
        for (Iterator<JsonNode> it = segments.elements(); it.hasNext(); ) {
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

    private ExtGridKeywords merge(ExtGridKeywords... extGridsKeywords) {
        final ObjectNode resultUserSection = mergeSections(extractSections(ExtGridKeywords::getUser, extGridsKeywords));
        final ObjectNode resultSiteSection = mergeSections(extractSections(ExtGridKeywords::getSite, extGridsKeywords));

        return ExtGridKeywords.of(resultUserSection, resultSiteSection);
    }

    private static Stream<ObjectNode> extractSections(Function<ExtGridKeywords, ObjectNode> sectionExtractor,
                                                      ExtGridKeywords... extGridsKeywords) {
        return Arrays.stream(extGridsKeywords)
                .map(extGridKeyword -> extractSection(sectionExtractor, extGridKeyword))
                .filter(Objects::nonNull);
    }

    private static ObjectNode extractSection(Function<ExtGridKeywords, ObjectNode> sectionExtractor,
                                             ExtGridKeywords extGridKeywords) {
        final ObjectNode sectionNode = extGridKeywords != null ? sectionExtractor.apply(extGridKeywords) : null;
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

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        return null;
    }

    private BidType getBidType(Bid bid, List<Imp> imps) {
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
