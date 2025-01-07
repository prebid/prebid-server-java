package org.prebid.server.bidder.pubmatic;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.CompositeBidderResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.pubmatic.model.request.PubmaticBidderImpExt;
import org.prebid.server.bidder.pubmatic.model.request.PubmaticExtDataAdServer;
import org.prebid.server.bidder.pubmatic.model.request.PubmaticWrapper;
import org.prebid.server.bidder.pubmatic.model.response.PubmaticBidExt;
import org.prebid.server.bidder.pubmatic.model.response.PubmaticBidResponse;
import org.prebid.server.bidder.pubmatic.model.response.PubmaticExtBidResponse;
import org.prebid.server.bidder.pubmatic.model.response.VideoCreativeInfo;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmatic;
import org.prebid.server.proto.openrtb.ext.request.pubmatic.ExtImpPubmaticKeyVal;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebid;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.proto.openrtb.ext.response.ExtIgi;
import org.prebid.server.proto.openrtb.ext.response.ExtIgiIgs;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.StreamUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PubmaticBidder implements Bidder<BidRequest> {

    private static final String DCTR_KEY_NAME = "key_val";
    private static final String PM_ZONE_ID_KEY_NAME = "pmZoneId";
    private static final String PM_ZONE_ID_OLD_KEY_NAME = "pmZoneID";
    private static final String IMP_EXT_AD_UNIT_KEY = "dfp_ad_unit_code";
    private static final String AD_SERVER_GAM = "gam";
    private static final String PREBID = "prebid";
    private static final String ACAT_EXT_REQUEST = "acat";
    private static final String WRAPPER_EXT_REQUEST = "wrapper";
    private static final String BIDDER_NAME = "pubmatic";
    private static final String AE = "ae";
    private static final String GP_ID = "gpid";
    private static final String IMP_EXT_PBADSLOT = "pbadslot";
    private static final String IMP_EXT_ADSERVER = "adserver";
    private static final List<String> IMP_EXT_DATA_RESERVED_FIELD = List.of(IMP_EXT_PBADSLOT, IMP_EXT_ADSERVER);
    private static final String DCTR_VALUE_FORMAT = "%s=%s";

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public PubmaticBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
        final List<BidderError> errors = new ArrayList<>();
        final List<Imp> validImps = new ArrayList<>();

        String publisherId = null;
        PubmaticWrapper wrapper;
        final List<String> acat;
        try {
            acat = extractAcat(request);
            wrapper = extractWrapper(request);
        } catch (IllegalArgumentException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        for (Imp imp : request.getImp()) {
            try {
                validateMediaType(imp);

                final PubmaticBidderImpExt impExt = parseImpExt(imp);
                final ExtImpPubmatic extImpPubmatic = impExt.getBidder();

                if (publisherId == null) {
                    publisherId = StringUtils.trimToNull(extImpPubmatic.getPublisherId());
                }

                wrapper = merge(wrapper, extImpPubmatic.getWrapper());

                validImps.add(modifyImp(imp, impExt));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (validImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final BidRequest modifiedBidRequest = modifyBidRequest(request, validImps, publisherId, wrapper, acat);
        return Result.of(Collections.singletonList(makeHttpRequest(modifiedBidRequest)), errors);
    }

    private List<String> extractAcat(BidRequest request) {
        final JsonNode bidderParams = getExtRequestPrebidBidderparams(request);
        final JsonNode acatNode = bidderParams != null ? bidderParams.get(ACAT_EXT_REQUEST) : null;

        return acatNode != null && acatNode.isArray()
                ? Arrays.stream(mapper.mapper().convertValue(acatNode, String[].class))
                .map(StringUtils::stripToEmpty)
                .toList()
                : null;
    }

    private PubmaticWrapper extractWrapper(BidRequest request) {
        final JsonNode pubmatic = getExtRequestPrebidBidderparams(request);
        final JsonNode wrapperNode = pubmatic != null ? pubmatic.get(WRAPPER_EXT_REQUEST) : null;

        return wrapperNode != null && wrapperNode.isObject()
                ? mapper.mapper().convertValue(wrapperNode, PubmaticWrapper.class)
                : null;
    }

    private static JsonNode getExtRequestPrebidBidderparams(BidRequest request) {
        final ExtRequest extRequest = request.getExt();
        final ExtRequestPrebid extRequestPrebid = extRequest != null ? extRequest.getPrebid() : null;
        final ObjectNode bidderParams = extRequestPrebid != null ? extRequestPrebid.getBidderparams() : null;
        return bidderParams != null ? bidderParams.get(BIDDER_NAME) : null;
    }

    private static void validateMediaType(Imp imp) {
        if (imp.getBanner() == null && imp.getVideo() == null && imp.getXNative() == null) {
            throw new PreBidException(
                    "Invalid MediaType. PubMatic only supports Banner, Video and Native. Ignoring ImpID=%s"
                            .formatted(imp.getId()));
        }
    }

    private PubmaticBidderImpExt parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), PubmaticBidderImpExt.class);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static PubmaticWrapper merge(PubmaticWrapper left, PubmaticWrapper right) {
        if (Objects.equals(left, right) || isWrapperValid(left)) {
            return left;
        } else if (right == null) {
            return left;
        } else if (left == null) {
            return right;
        }

        return PubmaticWrapper.of(
                ObjectUtils.defaultIfNull(stripToNull(left.getProfile()), right.getProfile()),
                ObjectUtils.defaultIfNull(stripToNull(left.getVersion()), right.getVersion()));
    }

    private static boolean isWrapperValid(PubmaticWrapper wrapper) {
        return wrapper != null
                && stripToNull(wrapper.getProfile()) != null
                && stripToNull(wrapper.getVersion()) != null;
    }

    private static Integer stripToNull(Integer value) {
        return value == null || value == 0 ? null : value;
    }

    private Imp modifyImp(Imp imp, PubmaticBidderImpExt impExt) {
        final Banner banner = imp.getBanner();
        final ExtImpPubmatic impExtBidder = impExt.getBidder();

        final ObjectNode newExt = makeKeywords(impExt);

        final Imp.ImpBuilder impBuilder = imp.toBuilder()
                .banner(banner != null ? assignSizesIfMissing(banner) : null)
                .audio(null)
                .bidfloor(resolveBidFloor(impExtBidder.getKadfloor(), imp.getBidfloor()))
                .ext(!newExt.isEmpty() ? newExt : null);

        enrichWithAdSlotParameters(impBuilder, impExtBidder.getAdSlot(), banner);

        return impBuilder.build();
    }

    private static Banner assignSizesIfMissing(Banner banner) {
        final List<Format> format = banner.getFormat();
        if ((banner.getW() != null && banner.getH() != null) || CollectionUtils.isEmpty(format)) {
            return banner;
        }

        final Format firstFormat = format.getFirst();
        return modifyWithSizeParams(banner, firstFormat.getW(), firstFormat.getH());
    }

    private static Banner modifyWithSizeParams(Banner banner, Integer width, Integer height) {
        return banner != null
                ? banner.toBuilder().w(width).h(height).build()
                : null;
    }

    private BigDecimal resolveBidFloor(String kadfloor, BigDecimal existingFloor) {
        final BigDecimal kadFloor = parseKadFloor(kadfloor);
        return ObjectUtils.allNotNull(kadFloor, existingFloor)
                ? kadFloor.max(existingFloor)
                : ObjectUtils.firstNonNull(kadFloor, existingFloor);
    }

    private static BigDecimal parseKadFloor(String kadFloorString) {
        if (StringUtils.isBlank(kadFloorString)) {
            return null;
        }
        try {
            return new BigDecimal(StringUtils.trimToEmpty(kadFloorString));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private ObjectNode makeKeywords(PubmaticBidderImpExt impExt) {
        final ObjectNode keywordsNode = mapper.mapper().createObjectNode();

        final ExtImpPubmatic extBidder = impExt.getBidder();
        putExtBidderKeywords(keywordsNode, extBidder);
        putExtDataKeywords(keywordsNode, impExt.getData(), extBidder.getDctr());

        if (impExt.getAe() != null) {
            keywordsNode.put(AE, impExt.getAe());
        }
        if (impExt.getGpId() != null) {
            keywordsNode.put(GP_ID, impExt.getGpId());
        }

        return keywordsNode;
    }

    private static void putExtBidderKeywords(ObjectNode keywords, ExtImpPubmatic extBidder) {
        for (ExtImpPubmaticKeyVal keyword : CollectionUtils.emptyIfNull(extBidder.getKeywords())) {
            if (CollectionUtils.isEmpty(keyword.getValue())) {
                continue;
            }
            keywords.put(keyword.getKey(), String.join(",", keyword.getValue()));
        }

        final JsonNode pmZoneIdKeyWords = keywords.remove(PM_ZONE_ID_OLD_KEY_NAME);
        final String pmZomeId = extBidder.getPmZoneId();
        if (StringUtils.isNotEmpty(pmZomeId)) {
            keywords.put(PM_ZONE_ID_KEY_NAME, pmZomeId);
        } else if (pmZoneIdKeyWords != null) {
            keywords.set(PM_ZONE_ID_KEY_NAME, pmZoneIdKeyWords);
        }
    }

    private void putExtDataKeywords(ObjectNode keywords, ObjectNode extData, String dctr) {
        final String newDctr = extractDctr(dctr, extData);
        if (StringUtils.isNotEmpty(newDctr)) {
            keywords.put(DCTR_KEY_NAME, newDctr);
        }

        final String adUnitCode = extractAdUnitCode(extData);
        if (StringUtils.isNotEmpty(adUnitCode)) {
            keywords.put(IMP_EXT_AD_UNIT_KEY, adUnitCode);
        }
    }

    private static String extractDctr(String firstDctr, ObjectNode extData) {
        if (extData == null) {
            return firstDctr;
        }

        return Stream.concat(
                        Stream.of(firstDctr),
                        StreamUtil.asStream(extData.fields())
                                .filter(entry -> !IMP_EXT_DATA_RESERVED_FIELD.contains(entry.getKey()))
                                .map(PubmaticBidder::buildDctrPart))
                .filter(Objects::nonNull)
                .collect(Collectors.joining("|"));
    }

    private static String buildDctrPart(Map.Entry<String, JsonNode> dctrPart) {
        final JsonNode value = dctrPart.getValue();
        final String valueAsString = value.isValueNode()
                ? StringUtils.trim(value.asText())
                : null;
        final String arrayAsString = valueAsString == null && value.isArray()
                ? StreamUtil.asStream(value.elements())
                .map(JsonNode::asText)
                .map(StringUtils::trim)
                .collect(Collectors.joining(","))
                : null;

        final String valuePart = ObjectUtils.firstNonNull(valueAsString, arrayAsString);

        return valuePart != null
                ? DCTR_VALUE_FORMAT.formatted(StringUtils.trim(dctrPart.getKey()), valuePart)
                : null;
    }

    private String extractAdUnitCode(ObjectNode extData) {
        if (extData == null) {
            return null;
        }

        final PubmaticExtDataAdServer extAdServer = extractAdServer(extData);
        final String adServerName = extAdServer != null ? extAdServer.getName() : null;
        final String adServerAdSlot = extAdServer != null ? extAdServer.getAdSlot() : null;

        return AD_SERVER_GAM.equals(adServerName) && StringUtils.isNotEmpty(adServerAdSlot)
                ? adServerAdSlot
                : Optional.ofNullable(extData.get(IMP_EXT_PBADSLOT))
                .map(JsonNode::asText)
                .orElse(null);
    }

    private PubmaticExtDataAdServer extractAdServer(ObjectNode extData) {
        try {
            return mapper.mapper().treeToValue(extData.get(IMP_EXT_ADSERVER), PubmaticExtDataAdServer.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static void enrichWithAdSlotParameters(Imp.ImpBuilder impBuilder, String adSlot, Banner banner) {
        final String trimmedAdSlot = StringUtils.trimToNull(adSlot);
        if (StringUtils.isEmpty(trimmedAdSlot)) {
            return;
        }

        if (!trimmedAdSlot.contains("@")) {
            impBuilder.tagid(trimmedAdSlot);
            return;
        }

        final String[] adSlotParams = trimmedAdSlot.split("@");
        final String trimmedParam0 = adSlotParams.length == 2 ? adSlotParams[0].trim() : null;
        final String trimmedParam1 = adSlotParams.length == 2 ? adSlotParams[1].trim() : null;

        if (adSlotParams.length != 2
                || StringUtils.isEmpty(trimmedParam0)
                || StringUtils.isEmpty(trimmedParam1)) {

            throw new PreBidException("Invalid adSlot '%s'".formatted(trimmedAdSlot));
        }

        impBuilder.tagid(trimmedParam0);

        final String[] adSize = trimmedParam1.toLowerCase().split("x");
        if (adSize.length != 2) {
            throw new PreBidException("Invalid size provided in adSlot '%s'".formatted(trimmedAdSlot));
        }

        final Integer width = parseAdSizeParam(adSize[0], "width", adSlot);

        final String[] heightParams = adSize[1].split(":");
        final Integer height = parseAdSizeParam(heightParams[0], "height", adSlot);

        impBuilder.banner(modifyWithSizeParams(banner, width, height));
    }

    private static Integer parseAdSizeParam(String number, String paramName, String adSlot) {
        try {
            return Integer.parseInt(number.trim());
        } catch (NumberFormatException e) {
            throw new PreBidException("Invalid %s provided in adSlot '%s'".formatted(paramName, adSlot));
        }
    }

    private BidRequest modifyBidRequest(BidRequest request,
                                        List<Imp> imps,
                                        String publisherId,
                                        PubmaticWrapper wrapper,
                                        List<String> acat) {

        return request.toBuilder()
                .imp(imps)
                .site(modifySite(request.getSite(), publisherId))
                .app(modifyApp(request.getApp(), publisherId))
                .ext(modifyExtRequest(request.getExt(), wrapper, acat))
                .build();
    }

    private static Site modifySite(Site site, String publisherId) {
        return publisherId != null && site != null
                ? site.toBuilder()
                .publisher(modifyPublisher(site.getPublisher(), publisherId))
                .build()
                : site;
    }

    private static App modifyApp(App app, String publisherId) {
        return publisherId != null && app != null
                ? app.toBuilder()
                .publisher(modifyPublisher(app.getPublisher(), publisherId))
                .build()
                : app;
    }

    private static Publisher modifyPublisher(Publisher publisher, String publisherId) {
        return publisher != null
                ? publisher.toBuilder().id(publisherId).build()
                : Publisher.builder().id(publisherId).build();
    }

    private ExtRequest modifyExtRequest(ExtRequest extRequest, PubmaticWrapper wrapper, List<String> acat) {
        final ObjectNode extNode = mapper.mapper().createObjectNode();

        if (wrapper != null) {
            extNode.putPOJO(WRAPPER_EXT_REQUEST, wrapper);
        }

        if (CollectionUtils.isNotEmpty(acat)) {
            extNode.putPOJO(ACAT_EXT_REQUEST, acat);
        }

        return extNode.isEmpty()
                ? extRequest
                : mapper.fillExtension(extRequest == null ? ExtRequest.empty() : extRequest, extNode);
    }

    private HttpRequest<BidRequest> makeHttpRequest(BidRequest request) {
        return BidderUtil.defaultRequest(request, endpointUrl, mapper);
    }

    /**
     * @deprecated for this bidder in favor of @link{makeBidderResponse} which supports additional response data
     */
    @Override
    @Deprecated(forRemoval = true)
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        return Result.withError(BidderError.generic("Deprecated adapter method invoked"));
    }

    @Override
    public CompositeBidderResponse makeBidderResponse(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final PubmaticBidResponse bidResponse = mapper.decodeValue(
                    httpCall.getResponse().getBody(), PubmaticBidResponse.class);
            final List<BidderError> errors = new ArrayList<>();

            return CompositeBidderResponse.builder()
                    .bids(extractBids(bidResponse, errors))
                    .igi(extractIgi(bidResponse))
                    .build();
        } catch (DecodeException | PreBidException e) {
            return CompositeBidderResponse.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(PubmaticBidResponse bidResponse, List<BidderError> bidderErrors) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse, bidderErrors);
    }

    private List<BidderBid> bidsFromResponse(PubmaticBidResponse bidResponse, List<BidderError> bidderErrors) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> resolveBidderBid(bid, bidResponse.getCur(), bidderErrors))
                .toList();
    }

    private BidderBid resolveBidderBid(Bid bid, String currency, List<BidderError> bidderErrors) {
        final List<String> cat = bid.getCat();
        final List<String> firstCat = CollectionUtils.isNotEmpty(cat)
                ? Collections.singletonList(cat.getFirst())
                : null;

        final PubmaticBidExt pubmaticBidExt = parseBidExt(bid.getExt(), bidderErrors);
        final Integer duration = getDuration(pubmaticBidExt);
        final BidType bidType = getBidType(pubmaticBidExt);

        final String bidAdm = bid.getAdm();
        final String resolvedAdm = bidAdm != null && bidType == BidType.xNative
                ? resolveNativeAdm(bidAdm, bidderErrors)
                : bidAdm;

        final Bid updatedBid = firstCat != null || duration != null || resolvedAdm != null
                ? bid.toBuilder()
                .cat(firstCat)
                .adm(resolvedAdm != null ? resolvedAdm : bidAdm)
                .ext(duration != null ? updateBidExtWithExtPrebid(duration, bid.getExt()) : bid.getExt())
                .build()
                : bid;

        return BidderBid.builder()
                .bid(updatedBid)
                .type(bidType)
                .bidCurrency(currency)
                .dealPriority(getDealPriority(pubmaticBidExt))
                .build();
    }

    private PubmaticBidExt parseBidExt(ObjectNode bidExt, List<BidderError> errors) {
        try {
            return bidExt != null ? mapper.mapper().treeToValue(bidExt, PubmaticBidExt.class) : null;
        } catch (JsonProcessingException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }
    }

    private static Integer getDuration(PubmaticBidExt bidExt) {
        return Optional.ofNullable(bidExt)
                .map(PubmaticBidExt::getVideo)
                .map(VideoCreativeInfo::getDuration)
                .orElse(null);
    }

    private static BidType getBidType(PubmaticBidExt bidExt) {
        final int bidType = Optional.ofNullable(bidExt)
                .map(PubmaticBidExt::getBidType)
                .orElse(0);

        return switch (bidType) {
            case 1 -> BidType.video;
            case 2 -> BidType.xNative;
            default -> BidType.banner;
        };
    }

    private String resolveNativeAdm(String adm, List<BidderError> bidderErrors) {
        final JsonNode admNode;
        try {
            admNode = mapper.mapper().readTree(adm);
        } catch (JsonProcessingException e) {
            bidderErrors.add(BidderError.badServerResponse("Unable to parse native adm: %s".formatted(adm)));
            return null;
        }

        final JsonNode nativeNode = admNode.get("native");
        if (!nativeNode.isMissingNode()) {
            return nativeNode.toString();
        }

        return null;
    }

    private ObjectNode updateBidExtWithExtPrebid(Integer duration, ObjectNode extBid) {
        final ExtBidPrebid extBidPrebid = ExtBidPrebid.builder().video(ExtBidPrebidVideo.of(duration, null)).build();
        return extBid.set(PREBID, mapper.mapper().valueToTree(extBidPrebid));
    }

    private static Integer getDealPriority(PubmaticBidExt bidExt) {
        return Optional.ofNullable(bidExt)
                .map(PubmaticBidExt::getPrebidDealPriority)
                .orElse(null);
    }

    private static List<ExtIgi> extractIgi(PubmaticBidResponse bidResponse) {
        final List<ExtIgiIgs> igs = Optional.ofNullable(bidResponse)
                .map(PubmaticBidResponse::getExt)
                .map(PubmaticExtBidResponse::getFledgeAuctionConfigs)
                .orElse(Collections.emptyMap())
                .entrySet()
                .stream()
                .map(config -> ExtIgiIgs.builder().impId(config.getKey()).config(config.getValue()).build())
                .toList();

        return Collections.singletonList(ExtIgi.builder().igs(igs).build());
    }
}
