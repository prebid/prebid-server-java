package org.prebid.server.bidder.appnexus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.Endpoint;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.appnexus.model.ImpWithExtProperties;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusKeyVal;
import org.prebid.server.bidder.appnexus.proto.AppnexusReqExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusReqExtAppnexus;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtIncludeBrandCategory;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidPbs;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.appnexus.ExtImpAppnexus;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public class AppnexusBidder implements Bidder<BidRequest> {

    private static final int AD_POSITION_ABOVE_THE_FOLD = 1; // openrtb.AdPosition.AdPositionAboveTheFold
    private static final int AD_POSITION_BELOW_THE_FOLD = 3; // openrtb.AdPosition.AdPositionBelowTheFold
    private static final int MAX_IMP_PER_REQUEST = 10;
    private static final String POD_SEPARATOR = "_";
    private static final Map<Integer, String> IAB_CATEGORIES = new HashMap<>();

    private static final TypeReference<ExtPrebid<?, ExtImpAppnexus>> APPNEXUS_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAppnexus>>() {
            };

    static {
        IAB_CATEGORIES.put(1, "IAB20-3");
        IAB_CATEGORIES.put(2, "IAB18-5");
        IAB_CATEGORIES.put(3, "IAB10-1");
        IAB_CATEGORIES.put(4, "IAB2-3");
        IAB_CATEGORIES.put(5, "IAB19-8");
        IAB_CATEGORIES.put(6, "IAB22-1");
        IAB_CATEGORIES.put(7, "IAB18-1");
        IAB_CATEGORIES.put(8, "IAB12-3");
        IAB_CATEGORIES.put(9, "IAB5-1");
        IAB_CATEGORIES.put(10, "IAB4-5");
        IAB_CATEGORIES.put(11, "IAB13-4");
        IAB_CATEGORIES.put(12, "IAB8-7");
        IAB_CATEGORIES.put(13, "IAB9-7");
        IAB_CATEGORIES.put(14, "IAB7-1");
        IAB_CATEGORIES.put(15, "IAB20-18");
        IAB_CATEGORIES.put(16, "IAB10-7");
        IAB_CATEGORIES.put(17, "IAB19-18");
        IAB_CATEGORIES.put(18, "IAB13-6");
        IAB_CATEGORIES.put(19, "IAB18-4");
        IAB_CATEGORIES.put(20, "IAB1-5");
        IAB_CATEGORIES.put(21, "IAB1-6");
        IAB_CATEGORIES.put(22, "IAB3-4");
        IAB_CATEGORIES.put(23, "IAB19-13");
        IAB_CATEGORIES.put(24, "IAB22-2");
        IAB_CATEGORIES.put(25, "IAB3-9");
        IAB_CATEGORIES.put(26, "IAB17-18");
        IAB_CATEGORIES.put(27, "IAB19-6");
        IAB_CATEGORIES.put(28, "IAB1-7");
        IAB_CATEGORIES.put(29, "IAB9-30");
        IAB_CATEGORIES.put(30, "IAB20-7");
        IAB_CATEGORIES.put(31, "IAB20-17");
        IAB_CATEGORIES.put(32, "IAB7-32");
        IAB_CATEGORIES.put(33, "IAB16-5");
        IAB_CATEGORIES.put(34, "IAB19-34");
        IAB_CATEGORIES.put(35, "IAB11-5");
        IAB_CATEGORIES.put(36, "IAB12-3");
        IAB_CATEGORIES.put(37, "IAB11-4");
        IAB_CATEGORIES.put(38, "IAB12-3");
        IAB_CATEGORIES.put(39, "IAB9-30");
        IAB_CATEGORIES.put(41, "IAB7-44");
        IAB_CATEGORIES.put(42, "IAB7-1");
        IAB_CATEGORIES.put(43, "IAB7-30");
        IAB_CATEGORIES.put(50, "IAB19-30");
        IAB_CATEGORIES.put(51, "IAB17-12");
        IAB_CATEGORIES.put(52, "IAB19-30");
        IAB_CATEGORIES.put(53, "IAB3-1");
        IAB_CATEGORIES.put(55, "IAB13-2");
        IAB_CATEGORIES.put(56, "IAB19-30");
        IAB_CATEGORIES.put(57, "IAB19-30");
        IAB_CATEGORIES.put(58, "IAB7-39");
        IAB_CATEGORIES.put(59, "IAB22-1");
        IAB_CATEGORIES.put(60, "IAB7-39");
        IAB_CATEGORIES.put(61, "IAB21-3");
        IAB_CATEGORIES.put(62, "IAB5-1");
        IAB_CATEGORIES.put(63, "IAB12-3");
        IAB_CATEGORIES.put(64, "IAB20-18");
        IAB_CATEGORIES.put(65, "IAB11-2");
        IAB_CATEGORIES.put(66, "IAB17-18");
        IAB_CATEGORIES.put(67, "IAB9-9");
        IAB_CATEGORIES.put(68, "IAB9-5");
        IAB_CATEGORIES.put(69, "IAB7-44");
        IAB_CATEGORIES.put(71, "IAB22-3");
        IAB_CATEGORIES.put(73, "IAB19-30");
        IAB_CATEGORIES.put(74, "IAB8-5");
        IAB_CATEGORIES.put(78, "IAB22-1");
        IAB_CATEGORIES.put(85, "IAB12-2");
        IAB_CATEGORIES.put(86, "IAB22-3");
        IAB_CATEGORIES.put(87, "IAB11-3");
        IAB_CATEGORIES.put(112, "IAB7-32");
        IAB_CATEGORIES.put(113, "IAB7-32");
        IAB_CATEGORIES.put(114, "IAB7-32");
        IAB_CATEGORIES.put(115, "IAB7-32");
        IAB_CATEGORIES.put(118, "IAB9-5");
        IAB_CATEGORIES.put(119, "IAB9-5");
        IAB_CATEGORIES.put(120, "IAB9-5");
        IAB_CATEGORIES.put(121, "IAB9-5");
        IAB_CATEGORIES.put(122, "IAB9-5");
        IAB_CATEGORIES.put(123, "IAB9-5");
        IAB_CATEGORIES.put(124, "IAB9-5");
        IAB_CATEGORIES.put(125, "IAB9-5");
        IAB_CATEGORIES.put(126, "IAB9-5");
        IAB_CATEGORIES.put(127, "IAB22-1");
        IAB_CATEGORIES.put(132, "IAB1-2");
        IAB_CATEGORIES.put(133, "IAB19-30");
        IAB_CATEGORIES.put(137, "IAB3-9");
        IAB_CATEGORIES.put(138, "IAB19-3");
        IAB_CATEGORIES.put(140, "IAB2-3");
        IAB_CATEGORIES.put(141, "IAB2-1");
        IAB_CATEGORIES.put(142, "IAB2-3");
        IAB_CATEGORIES.put(143, "IAB17-13");
        IAB_CATEGORIES.put(166, "IAB11-4");
        IAB_CATEGORIES.put(175, "IAB3-1");
        IAB_CATEGORIES.put(176, "IAB13-4");
        IAB_CATEGORIES.put(182, "IAB8-9");
        IAB_CATEGORIES.put(183, "IAB3-5");
    }

    private final String endpointUrl;
    private final JacksonMapper mapper;

    private final Random rand = new Random();

    public AppnexusBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final String defaultDisplayManagerVer = makeDefaultDisplayManagerVer(bidRequest);
        final List<Imp> processedImps = new ArrayList<>();
        final Set<String> uniqueIds = new HashSet<>();
        Boolean generateAdPodId = null;

        for (final Imp imp : bidRequest.getImp()) {
            try {
                final ImpWithExtProperties impWithExtProperties = processImp(imp, defaultDisplayManagerVer);
                final Boolean impGenerateAdPodId = impWithExtProperties.getGenerateAdPodId();

                generateAdPodId = ObjectUtils.defaultIfNull(generateAdPodId, impGenerateAdPodId);
                if (!Objects.equals(generateAdPodId, impGenerateAdPodId)) {
                    return Result.withError(BidderError.badInput(
                            "Generate ad pod option should be same for all pods in request"));
                }

                processedImps.add(impWithExtProperties.getImp());
                final String memberId = impWithExtProperties.getMemberId();
                if (memberId != null) {
                    uniqueIds.add(memberId);
                }
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final String url = constructUrl(uniqueIds, errors);
        return Result.of(constructRequests(bidRequest, processedImps, url, generateAdPodId), errors);
    }

    private String makeDefaultDisplayManagerVer(BidRequest bidRequest) {
        if (bidRequest.getApp() != null) {
            final ExtApp extApp = bidRequest.getApp().getExt();
            final ExtAppPrebid prebid = extApp != null ? extApp.getPrebid() : null;
            if (prebid != null) {
                final String source = prebid.getSource();
                final String version = prebid.getVersion();

                if (source != null && version != null) {
                    return String.format("%s-%s", source, version);
                }
            }
        }
        return null;
    }

    /**
     * The Appnexus API requires a Member ID in the URL if it present in the request. This means the request may fail
     * if different impressions have different member IDs.
     */
    private static void validateMemberId(Set<String> uniqueIds) {
        if (uniqueIds.size() > 1) {
            throw new PreBidException(
                    String.format("All request.imp[i].ext.appnexus.member params must match. Request contained: %s",
                            String.join(", ", uniqueIds)));
        }
    }

    private static boolean isVideoRequest(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        final ExtRequestPrebid prebid = requestExt != null ? requestExt.getPrebid() : null;
        final ExtRequestPrebidPbs pbs = prebid != null ? prebid.getPbs() : null;
        final String endpointName = pbs != null ? pbs.getEndpoint() : null;

        return StringUtils.equals(endpointName, Endpoint.openrtb2_video.value());
    }

    private ExtRequest updateRequestExt(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        if (isIncludeBrandCategory(requestExt)) {
            return updateRequestExt(requestExt, true, null);
        }
        return requestExt;
    }

    private ExtRequest updateRequestExt(ExtRequest requestExt, boolean includeBrandCategory, String adpodId) {
        return mapper.fillExtension(
                ExtRequest.of(requestExt.getPrebid()),
                AppnexusReqExt.of(AppnexusReqExtAppnexus.of(includeBrandCategory, includeBrandCategory, adpodId)));
    }

    private ExtRequest updateRequestExtForVideo(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        return updateRequestExt(requestExt, isIncludeBrandCategory(requestExt), Long.toUnsignedString(rand.nextLong()));
    }

    private static boolean isIncludeBrandCategory(ExtRequest extRequest) {
        final ExtRequestPrebid prebid = extRequest != null ? extRequest.getPrebid() : null;
        final ExtRequestTargeting targeting = prebid != null ? prebid.getTargeting() : null;
        final ExtIncludeBrandCategory includebrandcategory = targeting != null
                ? targeting.getIncludebrandcategory()
                : null;
        return includebrandcategory != null;
    }

    private String constructUrl(Set<String> ids, List<BidderError> errors) {
        if (CollectionUtils.isNotEmpty(ids)) {
            final String url = String.format("%s?member_id=%s", endpointUrl, ids.iterator().next());
            try {
                validateMemberId(ids);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
            return url;
        }
        return endpointUrl;
    }

    private List<HttpRequest<BidRequest>> constructRequests(BidRequest bidRequest,
                                                            List<Imp> imps,
                                                            String url,
                                                            Boolean generateAdPodId) {
        if (isVideoRequest(bidRequest) && BooleanUtils.isTrue(generateAdPodId)) {
            return groupImpsByPod(imps)
                    .values().stream()
                    .map(podImps -> splitHttpRequests(bidRequest, updateRequestExtForVideo(bidRequest), podImps, url))
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList());
        } else {
            return splitHttpRequests(bidRequest, updateRequestExt(bidRequest), imps, url);
        }
    }

    private Map<String, List<Imp>> groupImpsByPod(List<Imp> processedImps) {
        return processedImps.stream()
                .collect(Collectors.groupingBy(imp -> StringUtils.substringBefore(imp.getId(), POD_SEPARATOR)));
    }

    private List<HttpRequest<BidRequest>> splitHttpRequests(BidRequest bidRequest,
                                                            ExtRequest requestExt,
                                                            List<Imp> imps,
                                                            String url) {
        final List<HttpRequest<BidRequest>> result = ListUtils.partition(imps, MAX_IMP_PER_REQUEST)
                .stream()
                .map(impsChunk -> createHttpRequest(bidRequest, requestExt, impsChunk, url))
                .collect(Collectors.toList());

        return result.isEmpty()
                ? Collections.singletonList(createHttpRequest(bidRequest, requestExt, imps, url))
                : result;
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest bidRequest,
                                                      ExtRequest requestExt,
                                                      List<Imp> imps,
                                                      String url) {
        final BidRequest outgoingRequest = bidRequest.toBuilder()
                .imp(imps)
                .ext(requestExt)
                .build();

        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .body(mapper.encode(outgoingRequest))
                .headers(HttpUtil.headers())
                .payload(outgoingRequest)
                .build();
    }

    private ImpWithExtProperties processImp(Imp imp, String defaultDisplayManagerVer) {
        if (imp.getAudio() != null) {
            throw new PreBidException(
                    String.format("Appnexus doesn't support audio Imps. Ignoring Imp ID=%s", imp.getId()));
        }

        final ExtImpAppnexus appnexusExt = parseAndValidateAppnexusExt(imp);

        final Imp.ImpBuilder impBuilder = imp.toBuilder()
                .banner(makeBanner(imp.getBanner(), appnexusExt))
                .ext(mapper.mapper().valueToTree(makeAppnexusImpExt(appnexusExt)));

        final String invCode = appnexusExt.getInvCode();
        if (StringUtils.isNotBlank(invCode)) {
            impBuilder.tagid(invCode);
        }

        final BigDecimal reserve = appnexusExt.getReserve();
        if (!BidderUtil.isValidPrice(imp.getBidfloor()) && BidderUtil.isValidPrice(reserve)) {
            impBuilder.bidfloor(reserve); // This will be broken for non-USD currency.
        }

        // Populate imp.displaymanagerver if the SDK failed to do it.
        if (StringUtils.isBlank(imp.getDisplaymanagerver()) && StringUtils.isNotBlank(defaultDisplayManagerVer)) {
            impBuilder.displaymanagerver(defaultDisplayManagerVer);
        }

        return ImpWithExtProperties.of(impBuilder.build(), appnexusExt.getMember(),
                appnexusExt.getGenerateAdPodId());
    }

    private static AppnexusImpExt makeAppnexusImpExt(ExtImpAppnexus appnexusExt) {
        return AppnexusImpExt.of(
                AppnexusImpExtAppnexus.of(appnexusExt.getPlacementId(), makeKeywords(appnexusExt.getKeywords()),
                        appnexusExt.getTrafficSourceCode(), appnexusExt.getUsePmtRule(),
                        appnexusExt.getPrivateSizes()));
    }

    private static Banner makeBanner(Banner banner, ExtImpAppnexus appnexusExt) {
        Banner result = null;
        if (banner != null) {
            final String position = appnexusExt.getPosition();
            final Integer posAbove = Objects.equals(position, "above") ? AD_POSITION_ABOVE_THE_FOLD : null;
            final Integer posBelow = Objects.equals(position, "below") ? AD_POSITION_BELOW_THE_FOLD : null;
            final Integer pos = posAbove != null ? posAbove : posBelow;

            final boolean isFormatsPresent = CollectionUtils.isNotEmpty(banner.getFormat());
            final Integer width = isFormatsPresent && banner.getW() == null && banner.getH() == null
                    ? banner.getFormat().get(0).getW() : banner.getW();

            final Integer height = isFormatsPresent && banner.getH() == null && banner.getW() == null
                    ? banner.getFormat().get(0).getH() : banner.getH();

            if (pos != null || !Objects.equals(width, banner.getW()) || !Objects.equals(height, banner.getH())) {
                result = banner.toBuilder()
                        .pos(pos)
                        .w(width)
                        .h(height)
                        .build();
            } else {
                result = banner;
            }
        }
        return result;
    }

    private static String makeKeywords(List<AppnexusKeyVal> keywords) {
        if (CollectionUtils.isEmpty(keywords)) {
            return null;
        }

        final List<String> kvs = new ArrayList<>();
        for (AppnexusKeyVal keyVal : keywords) {
            final String key = keyVal.getKey();
            final List<String> values = keyVal.getValue();
            if (values == null || values.isEmpty()) {
                kvs.add(key);
            } else {
                for (String value : values) {
                    kvs.add(String.format("%s=%s", key, value));
                }
            }
        }

        return String.join(",", kvs);
    }

    private ExtImpAppnexus parseAndValidateAppnexusExt(Imp imp) {
        ExtImpAppnexus ext;
        try {
            ext = mapper.mapper().convertValue(imp.getExt(), APPNEXUS_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }

        // Accept legacy Appnexus parameters if we don't have modern ones
        // Don't worry if both is set as validation rules should prevent, and this is temporary anyway.
        boolean setPlacementId = ext.getPlacementId() == null && ext.getLegacyPlacementId() != null;
        boolean setInvCode = ext.getInvCode() == null && ext.getLegacyInvCode() != null;
        boolean setTrafficSourceCode = ext.getTrafficSourceCode() == null && ext.getLegacyTrafficSourceCode() != null;
        if (setPlacementId || setInvCode || setTrafficSourceCode) {
            ext = ext.toBuilder()
                    .placementId(setPlacementId ? ext.getLegacyPlacementId() : ext.getPlacementId())
                    .invCode(setInvCode ? ext.getLegacyInvCode() : ext.getInvCode())
                    .trafficSourceCode(setTrafficSourceCode ? ext.getLegacyTrafficSourceCode()
                            : ext.getTrafficSourceCode())
                    .build();
        }

        final Integer placementId = ext.getPlacementId();
        if ((placementId == null || Objects.equals(placementId, 0))
                && (StringUtils.isBlank(ext.getInvCode()) || StringUtils.isBlank(ext.getMember()))) {
            throw new PreBidException("No placement or member+invcode provided");
        }

        return ext;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        try {
            final BidResponse bidResponse = mapper.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.withValues(extractBids(bidResponse));
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> bidderBid(bid, bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private BidderBid bidderBid(Bid bid, String currency) {
        final AppnexusBidExtAppnexus appnexus = parseAppnexusBidExt(bid.getExt()).getAppnexus();
        if (appnexus == null) {
            throw new PreBidException("bidResponse.bid.ext.appnexus should be defined");
        }

        final String iabCategory = iabCategory(appnexus.getBrandCategoryId());

        List<String> cat = bid.getCat();
        if (iabCategory != null) {
            cat = Collections.singletonList(iabCategory);
        } else if (CollectionUtils.isNotEmpty(bid.getCat())) {
            // create empty categories array to force bid to be rejected
            cat = Collections.emptyList();
        }

        final Bid modifiedBid = bid.toBuilder().cat(cat).build();
        return BidderBid.of(modifiedBid, bidType(appnexus.getBidAdType()), currency);
    }

    private static String iabCategory(Integer brandId) {
        if (brandId == null) {
            return null;
        }
        return IAB_CATEGORIES.get(brandId);
    }

    private static BidType bidType(Integer bidAdType) {
        if (bidAdType == null) {
            throw new PreBidException("bidResponse.bid.ext.appnexus.bid_ad_type should be defined");
        }

        switch (bidAdType) {
            case 0:
                return BidType.banner;
            case 1:
                return BidType.video;
            case 2:
                return BidType.audio;
            case 3:
                return BidType.xNative;
            default:
                throw new PreBidException(
                        String.format("Unrecognized bid_ad_type in response from appnexus: %s", bidAdType));
        }
    }

    private AppnexusBidExt parseAppnexusBidExt(ObjectNode bidExt) {
        if (bidExt == null) {
            throw new PreBidException("bidResponse.bid.ext should be defined for appnexus");
        }

        final AppnexusBidExt appnexusBidExt;
        try {
            appnexusBidExt = mapper.mapper().treeToValue(bidExt, AppnexusBidExt.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
        return appnexusBidExt;
    }
}
