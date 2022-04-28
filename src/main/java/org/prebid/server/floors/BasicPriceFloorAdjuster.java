package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.floors.model.PriceFloorEnforcement;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountPriceFloorsConfig;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

public class BasicPriceFloorAdjuster implements PriceFloorAdjuster {

    private static final int ADJUSTMENT_SCALE = 4;

    @Override
    public BigDecimal adjustForImp(Imp imp, String bidder, BidRequest bidRequest, Account account) {
        final ExtRequestBidAdjustmentFactors extractBidAdjustmentFactors = extractBidAdjustmentFactors(bidRequest);
        final BigDecimal impBidFloor = imp.getBidfloor();

        if (!shouldAdjustBidFloor(bidRequest, account) || impBidFloor == null || extractBidAdjustmentFactors == null) {
            return impBidFloor;
        }

        final BigDecimal factor = resolveAdjustmentFactor(imp, extractBidAdjustmentFactors, bidder);

        return factor != null
                ? BidderUtil.roundFloor(impBidFloor.divide(factor, ADJUSTMENT_SCALE, RoundingMode.HALF_EVEN))
                : impBidFloor;
    }

    private static boolean shouldAdjustBidFloor(BidRequest bidRequest, Account account) {
        final Boolean shouldAdjustBidFloor =
                ObjectUtils.defaultIfNull(
                        shouldAdjustBidFloorByRequest(bidRequest),
                        shouldAdjustBidFloorByAccount(account));

        return ObjectUtils.defaultIfNull(shouldAdjustBidFloor, true);
    }

    private static Boolean shouldAdjustBidFloorByRequest(BidRequest bidRequest) {
        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid extPrebid = ObjectUtil.getIfNotNull(extRequest, ExtRequest::getPrebid);
        final PriceFloorRules floorRules = ObjectUtil.getIfNotNull(extPrebid, ExtRequestPrebid::getFloors);
        final PriceFloorEnforcement enforcement = ObjectUtil.getIfNotNull(floorRules, PriceFloorRules::getEnforcement);

        return ObjectUtil.getIfNotNull(enforcement, PriceFloorEnforcement::getBidAdjustment);
    }

    private static Boolean shouldAdjustBidFloorByAccount(Account account) {
        final AccountAuctionConfig auctionConfig = ObjectUtil.getIfNotNull(account, Account::getAuction);
        final AccountPriceFloorsConfig floorsConfig =
                ObjectUtil.getIfNotNull(auctionConfig, AccountAuctionConfig::getPriceFloors);

        return ObjectUtil.getIfNotNull(floorsConfig, AccountPriceFloorsConfig::getAdjustForBidAdjustment);
    }

    private static ExtRequestBidAdjustmentFactors extractBidAdjustmentFactors(BidRequest bidRequest) {
        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid extPrebid = ObjectUtil.getIfNotNull(extRequest, ExtRequest::getPrebid);

        return ObjectUtil.getIfNotNull(extPrebid, ExtRequestPrebid::getBidadjustmentfactors);
    }

    private static BigDecimal resolveAdjustmentFactor(Imp imp,
                                                      ExtRequestBidAdjustmentFactors adjustmentFactors,
                                                      String bidder) {

        final EnumMap<ImpMediaType, Map<String, BigDecimal>> adjustmentFactorsByMediaTypes =
                adjustmentFactors.getMediatypes();

        final BigDecimal bidderAdjustmentFactor = adjustmentFactors.getAdjustments().get(bidder);
        final BigDecimal effectiveBidderAdjustmentFactor = bidderAdjustmentFactor != null
                ? bidderAdjustmentFactor
                : BigDecimal.ONE;

        if (MapUtils.isEmpty(adjustmentFactorsByMediaTypes)) {
            return effectiveBidderAdjustmentFactor;
        }

        final BigDecimal mediaTypeMinFactor = retrieveImpMediaTypes(imp).stream()
                .map(adjustmentFactorsByMediaTypes::get)
                .map(bidderToFactor -> MapUtils.isNotEmpty(bidderToFactor)
                        ? bidderToFactor.get(bidder)
                        : effectiveBidderAdjustmentFactor)
                .filter(Objects::nonNull)
                .min(Comparator.comparing(Function.identity()))
                .orElse(null);

        return createFactorValue(mediaTypeMinFactor, bidderAdjustmentFactor);
    }

    private static List<ImpMediaType> retrieveImpMediaTypes(Imp imp) {
        final List<ImpMediaType> availableMediaTypes = new ArrayList<>();

        if (imp.getBanner() != null) {
            availableMediaTypes.add(ImpMediaType.banner);
        }
        if (imp.getVideo() != null) {
            final Integer placement = imp.getVideo().getPlacement();
            if (placement == null || Objects.equals(placement, 1)) {
                availableMediaTypes.add(ImpMediaType.video);
            } else {
                availableMediaTypes.add(ImpMediaType.video_outstream);
            }
        }
        if (imp.getXNative() != null) {
            availableMediaTypes.add(ImpMediaType.xNative);
        }
        if (imp.getAudio() != null) {
            availableMediaTypes.add(ImpMediaType.audio);
        }

        return availableMediaTypes;
    }

    private static BigDecimal createFactorValue(BigDecimal mediaTypeFactor, BigDecimal adjustmentFactor) {
        if (mediaTypeFactor != null && adjustmentFactor != null) {
            return mediaTypeFactor.min(adjustmentFactor);
        } else if (mediaTypeFactor != null) {
            return mediaTypeFactor;
        } else if (adjustmentFactor != null) {
            return adjustmentFactor;
        }

        return BigDecimal.ONE;
    }
}
