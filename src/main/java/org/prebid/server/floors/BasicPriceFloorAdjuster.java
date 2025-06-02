package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.bidadjustments.FloorAdjustmentFactorResolver;
import org.prebid.server.bidadjustments.FloorAdjustmentsResolver;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.exception.PreBidException;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

public class BasicPriceFloorAdjuster implements PriceFloorAdjuster {

    private static final int ADJUSTMENT_SCALE = 4;
    private static final BiFunction<BigDecimal, BigDecimal, BigDecimal> DIVIDE_FUNCTION =
            (priceFloor, factor) -> priceFloor.divide(factor, ADJUSTMENT_SCALE, RoundingMode.HALF_EVEN);

    private final FloorAdjustmentFactorResolver floorAdjustmentFactorResolver;
    private final FloorAdjustmentsResolver floorAdjustmentsResolver;

    public BasicPriceFloorAdjuster(FloorAdjustmentFactorResolver floorAdjustmentFactorResolver,
                                   FloorAdjustmentsResolver floorAdjustmentsResolver) {

        this.floorAdjustmentFactorResolver = Objects.requireNonNull(floorAdjustmentFactorResolver);
        this.floorAdjustmentsResolver = Objects.requireNonNull(floorAdjustmentsResolver);
    }

    @Override
    public Price adjustForImp(Imp imp,
                              String bidder,
                              BidRequest bidRequest,
                              Account account,
                              List<String> debugWarnings) {

        final ExtRequestBidAdjustmentFactors bidAdjustmentFactors = extractBidAdjustmentFactors(bidRequest);
        final BigDecimal impBidFloor = imp.getBidfloor();

        if (!shouldAdjustBidFloor(bidRequest, account) || impBidFloor == null) {
            return Price.of(imp.getBidfloorcur(), impBidFloor);
        }

        final Set<ImpMediaType> mediaTypes = retrieveImpMediaTypes(imp);
        final Price adjustedBidFloor = adjustPrice(imp, bidder, impBidFloor, bidAdjustmentFactors, mediaTypes);

        try {
            return floorAdjustmentsResolver.resolve(adjustedBidFloor, bidRequest, mediaTypes, bidder);
        } catch (PreBidException e) {
            return adjustedBidFloor;
        }
    }

    private Price adjustPrice(Imp imp,
                              String bidder,
                              BigDecimal impBidFloor,
                              ExtRequestBidAdjustmentFactors bidAdjustmentFactors,
                              Set<ImpMediaType> mediaTypes) {

        if (bidAdjustmentFactors == null) {
            return Price.of(imp.getBidfloorcur(), impBidFloor);
        }

        final BigDecimal factor = floorAdjustmentFactorResolver.resolve(mediaTypes, bidAdjustmentFactors, bidder);
        final BigDecimal adjustedBidFloorValue = factor != null && factor.compareTo(BigDecimal.ONE) != 0
                ? BidderUtil.roundFloor(DIVIDE_FUNCTION.apply(impBidFloor, factor))
                : impBidFloor;

        return Price.of(imp.getBidfloorcur(), adjustedBidFloorValue);
    }

    private static ExtRequestBidAdjustmentFactors extractBidAdjustmentFactors(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getBidadjustmentfactors)
                .orElse(null);
    }

    private static boolean shouldAdjustBidFloor(BidRequest bidRequest, Account account) {
        final Boolean shouldAdjustBidFloor =
                ObjectUtils.defaultIfNull(
                        shouldAdjustBidFloorByRequest(bidRequest),
                        shouldAdjustBidFloorByAccount(account));

        return ObjectUtils.defaultIfNull(shouldAdjustBidFloor, true);
    }

    private static Set<ImpMediaType> retrieveImpMediaTypes(Imp imp) {
        final Set<ImpMediaType> availableMediaTypes = EnumSet.noneOf(ImpMediaType.class);

        if (imp.getBanner() != null) {
            availableMediaTypes.add(ImpMediaType.banner);
        }
        if (imp.getVideo() != null) {
            final Integer placement = imp.getVideo().getPlacement();
            if (placement == null || Objects.equals(placement, 1)) {
                availableMediaTypes.add(ImpMediaType.video_instream);
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
}
