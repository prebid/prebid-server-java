package org.prebid.server.bidder.appnexus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Source;
import com.iab.openrtb.request.SupplyChain;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.prebid.server.auction.model.Endpoint;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtCreative;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtVideo;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusKeyVal;
import org.prebid.server.bidder.appnexus.proto.AppnexusReqExtAppnexus;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidServer;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSource;
import org.prebid.server.proto.openrtb.ext.request.appnexus.ExtImpAppnexus;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.proto.openrtb.ext.response.ExtBidPrebidVideo;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.ObjectUtil;

import jakarta.validation.ValidationException;
import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class AppnexusBidder implements Bidder<BidRequest> {

    private static final int DEFAULT_PLATFORM_ID = 5;
    private static final int AD_POSITION_ABOVE_THE_FOLD = 1;
    private static final int AD_POSITION_BELOW_THE_FOLD = 3;
    private static final String POD_SEPARATOR = "_";
    private static final int MAX_IMP_PER_REQUEST = 10;

    private static final TypeReference<ExtPrebid<?, ExtImpAppnexus>> APPNEXUS_EXT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final TypeReference<Map<String, List<String>>> KEYWORDS_OBJECT_TYPE_REFERENCE =
            new TypeReference<>() {
            };
    private static final TypeReference<List<AppnexusKeyVal>> KEYWORDS_ARRAY_TYPE_REFERENCE =
            new TypeReference<>() {
            };

    private final String endpointUrl;
    private final Integer headerBiddingSource;
    private final Map<Integer, String> iabCategories;
    private final JacksonMapper mapper;

    public AppnexusBidder(String endpointUrl,
                          Integer platformId,
                          Map<Integer, String> iabCategories,
                          JacksonMapper mapper) {

        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.headerBiddingSource = ObjectUtils.defaultIfNull(platformId, DEFAULT_PLATFORM_ID);
        this.iabCategories = ObjectUtils.defaultIfNull(iabCategories, Collections.emptyMap());
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final String defaultDisplayManagerVer = defaultDisplayManagerVer(bidRequest);
        final SameValueValidator<String> memberValidator = SameValueValidator.create();
        final SameValueValidator<Boolean> generateAdPodIdValidator = SameValueValidator.create();
        final List<Imp> updatedImps = new ArrayList<>();
        final List<BidderError> errors = new ArrayList<>();

        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpAppnexus extImpAppnexus = parseImpExt(imp);
                validateExtImpAppnexus(extImpAppnexus, memberValidator, generateAdPodIdValidator);

                updatedImps.add(updateImp(imp, extImpAppnexus, defaultDisplayManagerVer));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            } catch (ValidationException e) {
                errors.add(BidderError.badInput(e.getMessage()));
                return Result.withErrors(errors);
            }
        }

        if (updatedImps.isEmpty()) {
            return Result.withErrors(errors);
        }

        final String requestEndpointName = extractEndpointName(bidRequest);
        final boolean isAmp = StringUtils.equals(requestEndpointName, Endpoint.openrtb2_amp.value());
        final boolean isVideo = StringUtils.equals(requestEndpointName, Endpoint.openrtb2_video.value());

        final String url;
        final BidRequest updatedBidRequest;
        try {
            url = makeUrl(memberValidator.getValue());
            updatedBidRequest = updateBidRequest(bidRequest, isAmp, isVideo);
        } catch (PreBidException e) {
            errors.add(BidderError.badInput(e.getMessage()));
            return Result.withErrors(errors);
        }

        final List<HttpRequest<BidRequest>> requests = isVideo && generateAdPodIdValidator.getValue()
                ? makePodRequests(updatedBidRequest, updatedImps, url)
                : splitHttpRequests(updatedBidRequest, updatedImps, url);

        return Result.of(requests, errors);
    }

    private String defaultDisplayManagerVer(BidRequest bidRequest) {
        final Optional<ExtAppPrebid> prebid = Optional.ofNullable(bidRequest.getApp())
                .map(App::getExt)
                .map(ExtApp::getPrebid);

        final String source = prebid.map(ExtAppPrebid::getSource).orElse(null);
        final String version = prebid.map(ExtAppPrebid::getVersion).orElse(null);

        return ObjectUtils.allNotNull(source, version)
                ? "%s-%s".formatted(source, version)
                : null;
    }

    private ExtImpAppnexus parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), APPNEXUS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static void validateExtImpAppnexus(ExtImpAppnexus extImpAppnexus,
                                               SameValueValidator<String> memberValidator,
                                               SameValueValidator<Boolean> generateAdPodIdValidator) {

        final int placementId = ObjectUtils.defaultIfNull(extImpAppnexus.getPlacementId(), 0);
        final String member = extImpAppnexus.getMember();
        if (placementId == 0 && StringUtils.isAnyBlank(extImpAppnexus.getInvCode(), member)) {
            throw new PreBidException("No placement or member+invcode provided");
        }

        if (StringUtils.isNotBlank(member) && memberValidator.isInvalid(member)) {
            throw new ValidationException("all request.imp[i].ext.prebid.bidder.appnexus.member params must match."
                    + " Request contained member IDs %s and %s".formatted(memberValidator.getValue(), member));
        }

        if (generateAdPodIdValidator.isInvalid(extImpAppnexus.isGenerateAdPodId())) {
            throw new ValidationException("generate ad pod option should be same for all pods in request");
        }
    }

    private Imp updateImp(Imp imp, ExtImpAppnexus extImpAppnexus, String defaultDisplayManagerVer) {
        final String invCode = extImpAppnexus.getInvCode();
        final BigDecimal impBidFloor = imp.getBidfloor();
        final BigDecimal extBidFloor = extImpAppnexus.getReserve();
        final String displayManagerVer = imp.getDisplaymanagerver();

        return imp.toBuilder()
                .tagid(StringUtils.isNotBlank(invCode) ? invCode : imp.getTagid())
                .bidfloor(!BidderUtil.isValidPrice(impBidFloor) && BidderUtil.isValidPrice(extBidFloor)
                        ? extBidFloor
                        : impBidFloor)
                .banner(updateBanner(imp.getBanner(), extImpAppnexus))
                .displaymanagerver(StringUtils.isBlank(displayManagerVer) && defaultDisplayManagerVer != null
                        ? defaultDisplayManagerVer
                        : displayManagerVer)
                .ext(makeImpExt(extImpAppnexus))
                .build();
    }

    private static Banner updateBanner(Banner banner, ExtImpAppnexus extImpAppnexus) {
        if (banner == null) {
            return null;
        }

        final Integer width = banner.getW();
        final Integer height = banner.getH();
        final List<Format> formats = banner.getFormat();
        final Format firstFormat = CollectionUtils.isNotEmpty(formats)
                ? formats.get(0)
                : null;

        final boolean replaceWithFirstFormat = firstFormat != null && width == null && height == null;
        final Integer position = resolvePosition(extImpAppnexus.getPosition());

        return position != null || replaceWithFirstFormat
                ? banner.toBuilder()
                .pos(position != null ? position : banner.getPos())
                .w(replaceWithFirstFormat ? firstFormat.getW() : width)
                .h(replaceWithFirstFormat ? firstFormat.getH() : height)
                .build()
                : banner;
    }

    private static Integer resolvePosition(String position) {
        if (position == null) {
            return null;
        }

        return switch (position) {
            case "above" -> AD_POSITION_ABOVE_THE_FOLD;
            case "below" -> AD_POSITION_BELOW_THE_FOLD;
            default -> null;
        };
    }

    private ObjectNode makeImpExt(ExtImpAppnexus extImpAppnexus) {
        final AppnexusImpExtAppnexus ext = AppnexusImpExtAppnexus.builder()
                .placementId(extImpAppnexus.getPlacementId())
                .trafficSourceCode(extImpAppnexus.getTrafficSourceCode())
                .keywords(readKeywords(extImpAppnexus.getKeywords()))
                .usePmtRule(extImpAppnexus.getUsePaymentRule())
                .privateSizes(extImpAppnexus.getPrivateSizes())
                .extInvCode(extImpAppnexus.getExtInvCode())
                .externalImpId(extImpAppnexus.getExternalImpId())
                .build();

        return mapper.mapper().valueToTree(AppnexusImpExt.of(ext));
    }

    private String readKeywords(JsonNode keywords) {
        if (keywords == null) {
            return null;
        }
        if (keywords.isObject()) {
            return readKeywordsFromObject(keywords);
        }
        if (keywords.isArray()) {
            return readKeywordsFromArray(keywords);
        }
        if (keywords.isTextual()) {
            return keywords.textValue();
        }
        throw new PreBidException("'keywords' field has the wrong type.");
    }

    private String readKeywordsFromObject(JsonNode keywords) {
        final Map<String, List<String>> keywordsMap;
        try {
            keywordsMap = mapper.mapper().convertValue(keywords, KEYWORDS_OBJECT_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }

        return keywordsMap.entrySet().stream()
                .flatMap(entry -> keywordsStreamFor(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(","));
    }

    private static Stream<String> keywordsStreamFor(String key, List<String> values) {
        return CollectionUtils.isNotEmpty(values)
                ? values.stream().map(value -> "%s=%s".formatted(key, StringUtils.defaultString(value)))
                : Stream.of(key);
    }

    private String readKeywordsFromArray(JsonNode keywords) {
        final List<AppnexusKeyVal> keywordsArray;
        try {
            keywordsArray = mapper.mapper().convertValue(keywords, KEYWORDS_ARRAY_TYPE_REFERENCE);
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage());
        }

        return keywordsArray.stream()
                .flatMap(entry -> keywordsStreamFor(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(","));
    }

    private String makeUrl(String member) {
        try {
            return member != null
                    ? new URIBuilder(endpointUrl).addParameter("member_id", member).build().toString()
                    : endpointUrl;
        } catch (URISyntaxException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static String extractEndpointName(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        final ExtRequestPrebidServer server = prebid != null ? prebid.getServer() : null;
        return server != null ? server.getEndpoint() : null;
    }

    private BidRequest updateBidRequest(BidRequest bidRequest, boolean isAmp, boolean isVideo) {
        final Source source = bidRequest.getSource();
        final SupplyChain supplyChain = supplyChain(source);

        final UpdateResult<Source> updatedSource = updateSource(source, supplyChain);
        final ExtRequest updatedExtRequest = updateExtRequest(bidRequest.getExt(), supplyChain, isAmp, isVideo);

        return bidRequest.toBuilder()
                .source(updatedSource.getValue())
                .ext(updatedExtRequest)
                .build();
    }

    private static SupplyChain supplyChain(Source source) {
        return Optional.ofNullable(source)
                .map(Source::getExt)
                .map(ExtSource::getSchain)
                .orElse(null);
    }

    private static UpdateResult<Source> updateSource(Source source, SupplyChain supplyChain) {
        if (supplyChain == null) {
            return UpdateResult.unaltered(source);
        }

        final Source updatedSource = source.toBuilder()
                .ext(Optional.of(source.getExt().getProperties())
                        .filter(map -> !map.isEmpty())
                        .map(AppnexusBidder::extSourceWithProperties)
                        .orElse(null))
                .build();

        return UpdateResult.updated(updatedSource);
    }

    private static ExtSource extSourceWithProperties(Map<String, JsonNode> properties) {
        final ExtSource extSource = ExtSource.of(null);
        extSource.addProperties(properties);
        return extSource;
    }

    private ExtRequest updateExtRequest(ExtRequest extRequest,
                                        SupplyChain supplyChain,
                                        boolean isAmp,
                                        boolean isVideo) {

        final ExtRequest updatedExtRequest = makeCopyOrNew(extRequest);

        if (supplyChain != null) {
            updatedExtRequest.addProperty("schain", mapper.mapper().valueToTree(supplyChain));
        }

        updatedExtRequest.addProperty("appnexus", updateReqExtAppnexus(
                updatedExtRequest.getProperty("appnexus"),
                updatedExtRequest,
                isAmp,
                isVideo));

        return updatedExtRequest;
    }

    private ExtRequest makeCopyOrNew(ExtRequest extRequest) {
        final ExtRequest copy = Optional.ofNullable(extRequest)
                .map(original -> ExtRequest.of(original.getPrebid()))
                .orElseGet(ExtRequest::empty);
        if (extRequest != null) {
            mapper.fillExtension(copy, extRequest.getProperties());
        }

        return copy;
    }

    private ObjectNode updateReqExtAppnexus(JsonNode appnexus, ExtRequest extRequest, boolean isAmp, boolean isVideo) {
        final AppnexusReqExtAppnexus originalAppnexus = appnexus != null ? parseReqExtAppnexus(appnexus) : null;

        final boolean brandCategoryPresent = Optional.ofNullable(extRequest.getPrebid())
                .map(ExtRequestPrebid::getTargeting)
                .map(ExtRequestTargeting::getIncludebrandcategory)
                .isPresent();

        final AppnexusReqExtAppnexus updatedAppnexus = Optional.ofNullable(originalAppnexus)
                .map(AppnexusReqExtAppnexus::toBuilder)
                .orElseGet(AppnexusReqExtAppnexus::builder)
                .brandCategoryUniqueness(brandCategoryPresent
                        ? Boolean.TRUE
                        : ObjectUtil.getIfNotNull(originalAppnexus, AppnexusReqExtAppnexus::getBrandCategoryUniqueness))
                .includeBrandCategory(brandCategoryPresent
                        ? Boolean.TRUE
                        : ObjectUtil.getIfNotNull(originalAppnexus, AppnexusReqExtAppnexus::getIncludeBrandCategory))
                .isAmp(BooleanUtils.toInteger(isAmp))
                .headerBiddingSource(headerBiddingSource + BooleanUtils.toInteger(isVideo))
                .build();

        return mapper.mapper().valueToTree(updatedAppnexus);
    }

    private AppnexusReqExtAppnexus parseReqExtAppnexus(JsonNode jsonNode) {
        try {
            return mapper.mapper().treeToValue(jsonNode, AppnexusReqExtAppnexus.class);
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private List<HttpRequest<BidRequest>> makePodRequests(BidRequest bidRequest,
                                                          List<Imp> imps,
                                                          String url) {

        return groupImpsByPod(imps)
                .values().stream()
                .map(podImps -> splitHttpRequests(withGeneratedPodId(bidRequest), podImps, url))
                .flatMap(Collection::stream)
                .toList();
    }

    private BidRequest withGeneratedPodId(BidRequest bidRequest) {
        final ExtRequest copy = makeCopyOrNew(bidRequest.getExt());
        ((ObjectNode) copy.getProperty("appnexus"))
                .put("adpod_id", Long.toUnsignedString(ThreadLocalRandom.current().nextLong()));

        return bidRequest.toBuilder().ext(copy).build();
    }

    private Map<String, List<Imp>> groupImpsByPod(List<Imp> processedImps) {
        return processedImps.stream()
                .collect(Collectors.groupingBy(imp -> StringUtils.substringBefore(imp.getId(), POD_SEPARATOR)));
    }

    private List<HttpRequest<BidRequest>> splitHttpRequests(BidRequest bidRequest,
                                                            List<Imp> imps,
                                                            String url) {

        return ListUtils.partition(imps, MAX_IMP_PER_REQUEST)
                .stream()
                .map(impsChunk -> createHttpRequest(bidRequest, impsChunk, url))
                .toList();
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest bidRequest, List<Imp> imps, String url) {
        return BidderUtil.defaultRequest(bidRequest.toBuilder().imp(imps).build(), url, mapper);
    }

    @Override
    public Result<List<BidderBid>> makeBids(BidderCall<BidRequest> httpCall, BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse, errors), errors);
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse, List<BidderError> errors) {
        if (bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())) {
            return Collections.emptyList();
        }

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .filter(Objects::nonNull)
                .map(bid -> toBidderBid(bid, bidResponse.getCur(), errors))
                .filter(Objects::nonNull)
                .toList();
    }

    private BidderBid toBidderBid(Bid bid, String currency, List<BidderError> errors) {
        final AppnexusBidExtAppnexus appnexus;
        final BidType bidType;
        try {
            appnexus = parseBidExtAppnexus(bid);
            bidType = bidType(appnexus);
        } catch (PreBidException e) {
            errors.add(BidderError.badServerResponse(e.getMessage()));
            return null;
        }

        return BidderBid.builder()
                .bid(bid.toBuilder().cat(bidCategories(bid, appnexus)).build())
                .type(bidType)
                .bidCurrency(currency)
                .dealPriority(appnexus != null ? appnexus.getDealPriority() : 0)
                .videoInfo(makeVideoInfo(appnexus))
                .build();
    }

    private AppnexusBidExtAppnexus parseBidExtAppnexus(Bid bid) {
        final ObjectNode extBid = bid.getExt();
        if (extBid == null) {
            throw new PreBidException("bidResponse.bid.ext should be defined for appnexus");
        }

        try {
            return mapper.mapper().treeToValue(extBid, AppnexusBidExt.class).getAppnexus();
        } catch (IllegalArgumentException | JsonProcessingException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    private static BidType bidType(AppnexusBidExtAppnexus appnexus) {
        final int bidAdType = appnexus != null ? appnexus.getBidAdType() : 0;
        return switch (bidAdType) {
            case 0 -> BidType.banner;
            case 1 -> BidType.video;
            case 3 -> BidType.xNative;
            default -> throw new PreBidException("Unrecognized bid_ad_type in response from appnexus: " + bidAdType);
        };
    }

    private List<String> bidCategories(Bid bid, AppnexusBidExtAppnexus appnexus) {
        final String iabCategory = Optional.ofNullable(appnexus)
                .map(AppnexusBidExtAppnexus::getBrandCategoryId)
                .map(iabCategories::get)
                .orElse(null);
        if (iabCategory != null) {
            return Collections.singletonList(iabCategory);
        }

        // create empty categories array to force bid to be rejected
        final List<String> cat = bid.getCat();
        return cat == null || cat.size() > 1 ? Collections.emptyList() : cat;
    }

    private static ExtBidPrebidVideo makeVideoInfo(AppnexusBidExtAppnexus appnexus) {
        final int duration = Optional.ofNullable(appnexus)
                .map(AppnexusBidExtAppnexus::getCreativeInfo)
                .map(AppnexusBidExtCreative::getVideo)
                .map(AppnexusBidExtVideo::getDuration)
                .orElse(0);

        return ExtBidPrebidVideo.of(duration, null);
    }
}
