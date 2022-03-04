package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.floors.model.PriceFloorEnforcement;
import org.prebid.server.floors.model.PriceFloorRules;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidadjustmentfactors;
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

    @Override
    public BigDecimal adjustForImp(Imp imp, String bidder, BidRequest bidRequest, Account account) {
        final ExtRequestBidadjustmentfactors extBidadjustmentfactors = extractBidadjustmentfactors(bidRequest);
        final BigDecimal impBidFloor = imp.getBidfloor();

        if (!shouldAdjustBidFloor(bidRequest, account) || impBidFloor == null || extBidadjustmentfactors == null) {
            return impBidFloor;
        }

        final BigDecimal factor = resolveAdjustmentFactor(imp, extBidadjustmentfactors, bidder);

        return factor != null
                ? BidderUtil.roundFloor(impBidFloor.divide(factor, 4, RoundingMode.HALF_EVEN))
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

    private static ExtRequestBidadjustmentfactors extractBidadjustmentfactors(BidRequest bidRequest) {
        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid extPrebid = ObjectUtil.getIfNotNull(extRequest, ExtRequest::getPrebid);

        return ObjectUtil.getIfNotNull(extPrebid, ExtRequestPrebid::getBidadjustmentfactors);
    }

    private static BigDecimal resolveAdjustmentFactor(Imp imp,
                                                      ExtRequestBidadjustmentfactors adjustmentFactors,
                                                      String bidder) {

        final EnumMap<ImpMediaType, Map<String, BigDecimal>> adjustmentFactorsByMediaTypes =
                adjustmentFactors.getMediatypes();

        final BigDecimal bidderAdjustmentFactor = adjustmentFactors.getAdjustments().get(bidder);

        if (MapUtils.isEmpty(adjustmentFactorsByMediaTypes)) {
            return oneOrMore(bidderAdjustmentFactor);
        }

        final List<ImpMediaType> impMediaTypes = retrieveImpMediaTypes(imp);
        final BigDecimal mediaTypeMinFactor = adjustmentFactorsByMediaTypes.entrySet().stream()
                .filter(entry -> entry.getKey() != null && impMediaTypes.contains(entry.getKey()))
                .map(Map.Entry::getValue)
                .filter(Objects::nonNull)
                .map(adjustments -> adjustments.get(bidder))
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
            if (Objects.equals(placement, 1)) {
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
        final BigDecimal effectiveFactor;

        if (mediaTypeFactor == null) {
            effectiveFactor = adjustmentFactor;
        } else if (adjustmentFactor == null) {
            effectiveFactor = mediaTypeFactor;
        } else {
            effectiveFactor = mediaTypeFactor.compareTo(adjustmentFactor) < 0 ? mediaTypeFactor : adjustmentFactor;
        }

        return oneOrMore(effectiveFactor);
    }

    private static BigDecimal oneOrMore(BigDecimal value) {

        return value != null && BigDecimal.ONE.compareTo(value) > 0 ? value : BigDecimal.ONE;
    }
}
