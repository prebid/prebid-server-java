package org.rtb.vexing.bidder.appnexus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.adapter.appnexus.model.AppnexusImpExt;
import org.rtb.vexing.adapter.appnexus.model.AppnexusImpExtAppnexus;
import org.rtb.vexing.adapter.appnexus.model.AppnexusKeyVal;
import org.rtb.vexing.bidder.Bidder;
import org.rtb.vexing.bidder.OpenRtbBidder;
import org.rtb.vexing.bidder.model.BidderBid;
import org.rtb.vexing.bidder.model.HttpCall;
import org.rtb.vexing.bidder.model.HttpRequest;
import org.rtb.vexing.bidder.model.Result;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.model.openrtb.ext.ExtPrebid;
import org.rtb.vexing.model.openrtb.ext.request.appnexus.ExtImpAppnexus;
import org.rtb.vexing.model.openrtb.ext.response.BidType;

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
 * <p>
 * Maintainer email: <a href="mailto:info@prebid.org">info@prebid.org</a>
 */
public class AppnexusBidder extends OpenRtbBidder {

    private static final Logger logger = LoggerFactory.getLogger(AppnexusBidder.class);

    private static final int AD_POSITION_ABOVE_THE_FOLD = 1; // openrtb.AdPosition.AdPositionAboveTheFold
    private static final int AD_POSITION_BELOW_THE_FOLD = 3; // openrtb.AdPosition.AdPositionBelowTheFold

    private static final TypeReference<ExtPrebid<?, ExtImpAppnexus>> APPNEXUS_EXT_TYPE_REFERENCE = new
            TypeReference<ExtPrebid<?, ExtImpAppnexus>>() {
            };

    private final String endpointUrl;

    public AppnexusBidder(String endpointUrl) {
        this.endpointUrl = validateUrl(Objects.requireNonNull(endpointUrl));
    }

    @Override
    public Result<List<HttpRequest>> makeHttpRequests(BidRequest bidRequest) {
        if (CollectionUtils.isEmpty(bidRequest.getImp())) {
            return Result.emptyHttpRequests();
        }

        final List<String> errors = new ArrayList<>();
        final List<Imp> processedImps = new ArrayList<>();
        final Set<String> memberIds = new HashSet<>();
        for (final Imp imp : bidRequest.getImp()) {
            try {
                final ImpWithMemberId impWithMemberId = makeImpWithMemberId(imp);
                processedImps.add(impWithMemberId.imp);
                memberIds.add(impWithMemberId.memberId);
            } catch (PreBidException e) {
                errors.add(e.getMessage());
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
                errors.add(e.getMessage());
            }
        } else {
            url = endpointUrl;
        }
        return Result.of(Collections.singletonList(
                HttpRequest.of(HttpMethod.POST, url, body(bidRequest, processedImps), headers())), errors);
    }

    private static String body(BidRequest bidRequest, List<Imp> imps) {
        return Json.encode(bidRequest.toBuilder().imp(imps).build());
    }

    /**
     * The Appnexus API requires a Member ID in the URL if it present in the request. This means the request may fail
     * if different impressions have different member IDs.
     * */
    private static void validateMemberId(Set<String> uniqueIds) {
        if (uniqueIds.size() > 1) {
            throw new PreBidException(
                    String.format("All request.imp[i].ext.appnexus.member params must match. Request contained: %s",
                        String.join(", ", uniqueIds)));
        }
    }

    private static ImpWithMemberId makeImpWithMemberId(Imp imp) {

        if (imp.getXNative() != null || imp.getAudio() != null) {
            throw new PreBidException(
                    String.format("Appnexus doesn't support audio or native Imps. Ignoring Imp ID=%s", imp.getId()));
        }

        final ExtImpAppnexus appnexusExt = parseAppnexusExt(imp);

        if ((appnexusExt.placementId == null || Objects.equals(appnexusExt.placementId, 0))
                && (StringUtils.isBlank(appnexusExt.invCode) || StringUtils.isBlank(appnexusExt.member))) {
            throw new PreBidException("No placement or member+invcode provided");
        }

        final Imp.ImpBuilder impBuilder = imp.toBuilder()
                .banner(makeBanner(imp.getBanner(), appnexusExt))
                .ext(Json.mapper.valueToTree(makeAppnexusImpExt(appnexusExt)));

        if (StringUtils.isNotBlank(appnexusExt.invCode)) {
            impBuilder.tagid(appnexusExt.invCode);
        }

        if (appnexusExt.reserve != null && appnexusExt.reserve.compareTo(BigDecimal.ZERO) > 0) {
            impBuilder.bidfloor(appnexusExt.reserve.floatValue()); // This will be broken for non-USD currency.
        }
        return ImpWithMemberId.of(impBuilder.build(), appnexusExt.member);
    }

    private static AppnexusImpExt makeAppnexusImpExt(ExtImpAppnexus appnexusExt) {
        return AppnexusImpExt.builder()
                .appnexus(AppnexusImpExtAppnexus.builder()
                    .placementId(appnexusExt.placementId)
                    .trafficSourceCode(appnexusExt.trafficSourceCode)
                    .keywords(makeKeywords(appnexusExt.keywords))
                    .build())
                .build();
    }

    private static Banner makeBanner(Banner banner, ExtImpAppnexus appnexusExt) {
        Banner result = null;
        if (banner != null) {
            final Integer posAbove = Objects.equals(appnexusExt.position, "above") ? AD_POSITION_ABOVE_THE_FOLD : null;
            final Integer posBelow = Objects.equals(appnexusExt.position, "below") ? AD_POSITION_BELOW_THE_FOLD : null;
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
            if (keyVal.values == null || keyVal.values.isEmpty()) {
                kvs.add(keyVal.key);
            } else {
                for (String value : keyVal.values) {
                    kvs.add(String.format("%s=%s", keyVal.key, value));
                }
            }
        }
        return kvs.stream().collect(Collectors.joining(","));
    }

    private static ExtImpAppnexus parseAppnexusExt(Imp imp) {
        try {
            return Json.mapper.<ExtPrebid<?, ExtImpAppnexus>>convertValue(imp.getExt(), APPNEXUS_EXT_TYPE_REFERENCE)
                    .bidder;
        } catch (IllegalArgumentException e) {
            logger.warn("Error occurred parsing appnexus parameters", e);
            throw new PreBidException(e.getMessage(), e);
        }
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall httpCall, BidRequest bidRequest) {
        try {
            return Result.of(extractBids(bidRequest, parseResponse(httpCall.response)), Collections.emptyList());
        } catch (PreBidException e) {
            return Result.of(Collections.emptyList(), Collections.singletonList(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        final Map<String, BidType> impidToBidType = impidToBidType(bidRequest);

        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, bidType(bid, impidToBidType)))
                .collect(Collectors.toList());
    }

    @Override
    public String name() {
        return "appnexus";
    }

    @Override
    public String cookieFamilyName() {
        return "adnxs";
    }

    @AllArgsConstructor(staticName = "of")
    @FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
    private static class ImpWithMemberId {
        Imp imp;
        String memberId;
    }
}
