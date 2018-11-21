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
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderUtil;
import org.prebid.server.bidder.appnexus.model.ImpWithMemberId;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusBidExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExt;
import org.prebid.server.bidder.appnexus.proto.AppnexusImpExtAppnexus;
import org.prebid.server.bidder.appnexus.proto.AppnexusKeyVal;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.appnexus.ExtImpAppnexus;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

    private static final TypeReference<ExtPrebid<?, ExtImpAppnexus>> APPNEXUS_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpAppnexus>>() {
            };

    private final String endpointUrl;

    public AppnexusBidder(String endpointUrl) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        if (CollectionUtils.isEmpty(bidRequest.getImp())) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        }

        final List<BidderError> errors = new ArrayList<>();
        final String defaultDisplayManagerVer = makeDefaultDisplayManagerVer(bidRequest, errors);

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

        final BidRequest outgoingRequest = bidRequest.toBuilder().imp(processedImps).build();
        final String body = Json.encode(outgoingRequest);

        return Result.of(
                Collections.singletonList(HttpRequest.of(HttpMethod.POST, url, body, BidderUtil.headers(),
                        outgoingRequest)), errors);
    }

    private static String makeDefaultDisplayManagerVer(BidRequest bidRequest, List<BidderError> errors) {
        if (bidRequest.getApp() != null) {
            try {
                final ExtAppPrebid prebid = Json.mapper.convertValue(bidRequest.getApp().getExt(),
                        ExtApp.class).getPrebid();
                if (prebid != null) {
                    final String source = prebid.getSource();
                    final String version = prebid.getVersion();

                    if (source != null && version != null) {
                        return String.format("%s-%s", source, version);
                    }
                }
            } catch (IllegalArgumentException e) {
                errors.add(BidderError.badInput(e.getMessage()));
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

    private static ImpWithMemberId makeImpWithMemberId(Imp imp, String defaultDisplayManagerVer) {
        if (imp.getAudio() != null) {
            throw new PreBidException(
                    String.format("Appnexus doesn't support audio Imps. Ignoring Imp ID=%s", imp.getId()));
        }

        final ExtImpAppnexus appnexusExt = parseAndValidateAppnexusExt(imp);

        final Imp.ImpBuilder impBuilder = imp.toBuilder()
                .banner(makeBanner(imp.getBanner(), appnexusExt))
                .ext(Json.mapper.valueToTree(makeAppnexusImpExt(appnexusExt)));

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

        return kvs.stream().collect(Collectors.joining(","));
    }

    private static ExtImpAppnexus parseAndValidateAppnexusExt(Imp imp) {
        ExtImpAppnexus ext;
        try {
            ext = Json.mapper.<ExtPrebid<?, ExtImpAppnexus>>convertValue(imp.getExt(), APPNEXUS_EXT_TYPE_REFERENCE)
                    .getBidder();
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
            final BidResponse bidResponse = Json.decodeValue(httpCall.getResponse().getBody(), BidResponse.class);
            return Result.of(extractBids(bidResponse), Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, bidType(bid), null))
                .collect(Collectors.toList());
    }

    private static BidType bidType(Bid bid) {
        final AppnexusBidExtAppnexus appnexus = parseAppnexusBidExt(bid.getExt()).getAppnexus();
        if (appnexus == null) {
            throw new PreBidException("bidResponse.bid.ext.appnexus should be defined");
        }

        final Integer bidAdType = appnexus.getBidAdType();
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

    private static AppnexusBidExt parseAppnexusBidExt(ObjectNode bidExt) {
        if (bidExt == null) {
            throw new PreBidException("bidResponse.bid.ext should be defined for appnexus");
        }

        final AppnexusBidExt appnexusBidExt;
        try {
            appnexusBidExt = Json.mapper.treeToValue(bidExt, AppnexusBidExt.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException(e.getMessage(), e);
        }
        return appnexusBidExt;
    }
}
