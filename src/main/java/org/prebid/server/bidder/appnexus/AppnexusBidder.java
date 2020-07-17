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
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.appnexus.model.ImpWithMemberId;
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
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.appnexus.ExtImpAppnexus;
import org.prebid.server.proto.openrtb.ext.response.BidType;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * AppNexus {@link Bidder} implementation.
 */
public class AppnexusBidder implements Bidder<BidRequest> {

    private static final int AD_POSITION_ABOVE_THE_FOLD = 1; // openrtb.AdPosition.AdPositionAboveTheFold
    private static final int AD_POSITION_BELOW_THE_FOLD = 3; // openrtb.AdPosition.AdPositionBelowTheFold
    private static final int MAX_IMP_PER_REQUEST = 10;
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
        IAB_CATEGORIES.put(7, "IAB18-1");
        IAB_CATEGORIES.put(8, "IAB14-1");
        IAB_CATEGORIES.put(9, "IAB5-1");
        IAB_CATEGORIES.put(10, "IAB4-5");
        IAB_CATEGORIES.put(11, "IAB13-4");
        IAB_CATEGORIES.put(13, "IAB19-2");
        IAB_CATEGORIES.put(12, "IAB8-7");
        IAB_CATEGORIES.put(14, "IAB7-1");
        IAB_CATEGORIES.put(15, "IAB20-18");
        IAB_CATEGORIES.put(16, "IAB10-7");
        IAB_CATEGORIES.put(17, "IAB19-18");
        IAB_CATEGORIES.put(18, "IAB13-6");
        IAB_CATEGORIES.put(19, "IAB18-4");
        IAB_CATEGORIES.put(20, "IAB1-5");
        IAB_CATEGORIES.put(21, "IAB1-6");
        IAB_CATEGORIES.put(22, "IAB19-28");
        IAB_CATEGORIES.put(23, "IAB19-13");
        IAB_CATEGORIES.put(24, "IAB22-2");
        IAB_CATEGORIES.put(25, "IAB3-9");
        IAB_CATEGORIES.put(26, "IAB17-26");
        IAB_CATEGORIES.put(27, "IAB19-6");
        IAB_CATEGORIES.put(28, "IAB1-7");
        IAB_CATEGORIES.put(29, "IAB9-5");
        IAB_CATEGORIES.put(30, "IAB20-7");
        IAB_CATEGORIES.put(31, "IAB20-17");
        IAB_CATEGORIES.put(32, "IAB7-32");
        IAB_CATEGORIES.put(33, "IAB16-5");
        IAB_CATEGORIES.put(34, "IAB19-34");
        IAB_CATEGORIES.put(37, "IAB11-4");
        IAB_CATEGORIES.put(39, "IAB9-30");
        IAB_CATEGORIES.put(41, "IAB7-44");
        IAB_CATEGORIES.put(51, "IAB17-12");
        IAB_CATEGORIES.put(53, "IAB3-1");
        IAB_CATEGORIES.put(55, "IAB13-2");
        IAB_CATEGORIES.put(61, "IAB21-3");
        IAB_CATEGORIES.put(62, "IAB6-4");
        IAB_CATEGORIES.put(63, "IAB15-10");
        IAB_CATEGORIES.put(65, "IAB11-2");
        IAB_CATEGORIES.put(67, "IAB9-9");
        IAB_CATEGORIES.put(69, "IAB7-1");
        IAB_CATEGORIES.put(71, "IAB22-2");
        IAB_CATEGORIES.put(74, "IAB8-5");
        IAB_CATEGORIES.put(87, "IAB3-7");
    }

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public AppnexusBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        final String defaultDisplayManagerVer = makeDefaultDisplayManagerVer(bidRequest);

        final List<Imp> processedImps = new ArrayList<>();
        final Set<String> memberIds = new HashSet<>();
        for (final Imp imp : bidRequest.getImp()) {
            try {
                final ImpWithMemberId impWithMemberId = makeImpWithMemberId(imp, defaultDisplayManagerVer);
                processedImps.add(impWithMemberId.getImp());
                memberIds.add(impWithMemberId.getMemberId());
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        final Set<String> uniqueIds = memberIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        final String url;
        if (CollectionUtils.isNotEmpty(uniqueIds)) {
            url = String.format("%s?member_id=%s", endpointUrl, uniqueIds.iterator().next());
            try {
                validateMemberId(uniqueIds);
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        } else {
            url = endpointUrl;
        }

        final ExtRequest updatedRequestExt = updatedRequestExt(bidRequest);
        final BidRequest outgoingRequest = updatedRequestExt != null
                ? bidRequest.toBuilder().ext(updatedRequestExt).build()
                : bidRequest;

        return Result.of(splitHttpRequests(outgoingRequest, processedImps, url, MAX_IMP_PER_REQUEST), errors);
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

    private ExtRequest updatedRequestExt(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        if (isIncludeBrandCategory(requestExt)) {
            return mapper.fillExtension(
                    ExtRequest.of(requestExt.getPrebid()),
                    AppnexusReqExt.of(AppnexusReqExtAppnexus.of(true, true)));
        }
        return null;
    }

    private static boolean isIncludeBrandCategory(ExtRequest extRequest) {
        final ExtRequestPrebid prebid = extRequest != null ? extRequest.getPrebid() : null;
        final ExtRequestTargeting targeting = prebid != null ? prebid.getTargeting() : null;
        final ExtIncludeBrandCategory includebrandcategory = targeting != null
                ? targeting.getIncludebrandcategory()
                : null;
        return includebrandcategory != null && includebrandcategory.getPrimaryAdserver() != 0;
    }

    private List<HttpRequest<BidRequest>> splitHttpRequests(BidRequest outgoingRequest, List<Imp> processedImps,
                                                            String url, int maxImpPerRequest) {
        // Let's say there are 35 impressions and limit impressions per request equals to 10.
        // In this case we need to create 4 requests with 10, 10, 10 and 5 impressions.
        // With this formula initial capacity=(35+10-1)/10 = 4
        final int impSize = processedImps.size();
        final int numberOfRequests = (impSize + maxImpPerRequest - 1) / maxImpPerRequest;
        final List<HttpRequest<BidRequest>> spitedRequests = new ArrayList<>(numberOfRequests);

        int startIndex = 0;
        boolean impsLeft = true;
        while (impsLeft) {
            int endIndex = startIndex + maxImpPerRequest;
            if (endIndex >= impSize) {
                impsLeft = false;
                endIndex = impSize;
            }
            spitedRequests.add(createHttpRequest(outgoingRequest, processedImps.subList(startIndex, endIndex), url));
            startIndex = endIndex;
        }

        return spitedRequests;
    }

    private HttpRequest<BidRequest> createHttpRequest(BidRequest bidRequest, List<Imp> imps, String url) {
        final BidRequest outgoingRequest = bidRequest.toBuilder().imp(imps).build();
        return HttpRequest.<BidRequest>builder()
                .method(HttpMethod.POST)
                .uri(url)
                .body(mapper.encode(outgoingRequest))
                .headers(HttpUtil.headers())
                .payload(outgoingRequest)
                .build();
    }

    private ImpWithMemberId makeImpWithMemberId(Imp imp, String defaultDisplayManagerVer) {
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
        if (reserve != null && reserve.compareTo(BigDecimal.ZERO) > 0) {
            impBuilder.bidfloor(reserve); // This will be broken for non-USD currency.
        }

        // Populate imp.displaymanagerver if the SDK failed to do it.
        if (StringUtils.isBlank(imp.getDisplaymanagerver()) && StringUtils.isNotBlank(defaultDisplayManagerVer)) {
            impBuilder.displaymanagerver(defaultDisplayManagerVer);
        }

        return ImpWithMemberId.of(impBuilder.build(), appnexusExt.getMember());
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
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }

    private List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
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
        if (iabCategory != null) {
            bid.setCat(Collections.singletonList(iabCategory));
        } else if (CollectionUtils.isNotEmpty(bid.getCat())) {
            //create empty categories array to force bid to be rejected
            bid.setCat(Collections.emptyList());
        }

        return BidderBid.of(bid, bidType(appnexus.getBidAdType()), currency);
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
