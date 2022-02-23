package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidadjustmentfactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class BasicPriceFloorAdjuster implements PriceFloorAdjuster {

    @Override
    public BigDecimal adjustForImp(Imp imp, String bidder, BidRequest bidRequest) {
        final ExtRequestBidadjustmentfactors extBidadjustmentfactors =
                extractBidadjustmentfactors(bidRequest);
        final BigDecimal impBidfloor = imp.getBidfloor();

        if (impBidfloor == null) {
            return null;
        }

        if (extBidadjustmentfactors == null) {
            return impBidfloor;
        }

        final BigDecimal factor = resolveBidFloor(imp, extBidadjustmentfactors, bidder);

        return factor != null
                ? BidderUtil.roundFloor(impBidfloor.divide(factor, factor.precision(), RoundingMode.HALF_EVEN))
                : impBidfloor;
    }

    private static ExtRequestBidadjustmentfactors extractBidadjustmentfactors(BidRequest bidRequest) {
        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid extPrebid = ObjectUtil.getIfNotNull(extRequest, ExtRequest::getPrebid);
        return ObjectUtil.getIfNotNull(extPrebid, ExtRequestPrebid::getBidadjustmentfactors);
    }

    private static BigDecimal resolveBidFloor(Imp imp, ExtRequestBidadjustmentfactors extBidadjustmentfactors, String bidder) {
        final EnumMap<ImpMediaType, Map<String, BigDecimal>> extBidadjustmentfactorsMediatypes =
                extBidadjustmentfactors.getMediatypes();

        if (MapUtils.isEmpty(extBidadjustmentfactorsMediatypes)) {
            return null;
        }

        final List<ImpMediaType> availableMediatypesForImp = retrieveImpMediatypes(imp);
        if (CollectionUtils.isEmpty(availableMediatypesForImp)) {
            return null;
        }

        final Map.Entry<ImpMediaType, Map<String, BigDecimal>> mediatypeMinFactor = extBidadjustmentfactorsMediatypes.entrySet().stream()
                .filter(e -> availableMediatypesForImp.contains(e.getKey()))
                .min(Comparator.comparing(e -> e.getValue().get(bidder)))
                .orElse(null);

        final BigDecimal adjustmentFactor = extBidadjustmentfactors.getAdjustments().get(bidder);

        return mediatypeMinFactor != null
                ? resolveCorrectAdjustment(mediatypeMinFactor.getValue().get(bidder), adjustmentFactor)
                : adjustmentFactor != null ? adjustmentFactor : BigDecimal.ONE;
    }

    private static List<ImpMediaType> retrieveImpMediatypes(Imp imp) {
        final List<ImpMediaType> availableMediatypes = new ArrayList<>();

        if (imp.getBanner() != null) {
            availableMediatypes.add(ImpMediaType.banner);
        }
        if (imp.getVideo() != null) {
            final Integer placement = imp.getVideo().getPlacement();
            if (placement != null) {
                availableMediatypes.add(resolveImpMediaTypeFromPlacement(placement));
            }
        }
        if (imp.getXNative() != null) {
            availableMediatypes.add(ImpMediaType.xNative);
        }
        if (imp.getAudio() != null) {
            availableMediatypes.add(ImpMediaType.audio);
        }

        return availableMediatypes;
    }

    private static ImpMediaType resolveImpMediaTypeFromPlacement(Integer placement) {
        if (placement != 1) {
            return ImpMediaType.video_outstream;
        } else {
            return ImpMediaType.video;
        }
    }

    private static BigDecimal resolveCorrectAdjustment(BigDecimal mediaTypeFactor, BigDecimal adjustmentFactor) {
        if (mediaTypeFactor == null) {
            return adjustmentFactor == null ? BigDecimal.ONE : adjustmentFactor;
        } else {
            if (adjustmentFactor != null) {
                return mediaTypeFactor.compareTo(adjustmentFactor) < 0 ? mediaTypeFactor : adjustmentFactor;
            } else {
                return mediaTypeFactor;
            }
        }
    }
}
