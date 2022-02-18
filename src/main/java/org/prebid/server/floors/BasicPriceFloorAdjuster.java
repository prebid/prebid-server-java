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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class BasicPriceFloorAdjuster implements PriceFloorAdjuster {

    @Override
    public BigDecimal adjustForImp(Imp imp, String bidder, BidRequest bidRequest) {
        final ExtRequestBidadjustmentfactors extBidadjustmentfactors =
                extractBidadjustmentfactors(bidRequest);

        if (extBidadjustmentfactors == null) {
            return imp.getBidfloor();
        }

        final ImpMediaType mediaType = chooseMediaType(imp, extBidadjustmentfactors, bidder);

        final BigDecimal factor = resolveBidAdjustmentFactor(extBidadjustmentfactors, mediaType, bidder);

        return imp.getBidfloor() != null
                ? BidderUtil.roundFloor(imp.getBidfloor().divide(factor, factor.precision(), RoundingMode.HALF_EVEN))
                : imp.getBidfloor();
    }

    private static ExtRequestBidadjustmentfactors extractBidadjustmentfactors(BidRequest bidRequest) {
        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid extPrebid = ObjectUtil.getIfNotNull(extRequest, ExtRequest::getPrebid);
        return ObjectUtil.getIfNotNull(extPrebid, ExtRequestPrebid::getBidadjustmentfactors);
    }

    private static ImpMediaType chooseMediaType(Imp imp, ExtRequestBidadjustmentfactors extBidadjustmentfactors, String bidder) {
        if (MapUtils.isEmpty(extBidadjustmentfactors.getMediatypes())) {
            return null;
        }

        final List<ImpMediaType> availableMediatypesForImp = retrieveImpMediatypes(imp);
        if (CollectionUtils.isEmpty(availableMediatypesForImp)) {
            return null;
        }

        final TreeMap<BigDecimal, ImpMediaType> factorToMediatype = new TreeMap<>();

        for (ImpMediaType mediaType : availableMediatypesForImp) {
            final Map<String, BigDecimal> mediaTypeValues = extBidadjustmentfactors.getMediatypes().get(mediaType);
            if (MapUtils.isEmpty(mediaTypeValues)) {
                continue;
            }

            final BigDecimal factor = mediaTypeValues.get(bidder);
            if (factor == null) {
                continue;
            }
            factorToMediatype.put(factor.setScale(4, RoundingMode.UNNECESSARY), mediaType);
        }

        return MapUtils.isEmpty(factorToMediatype) ? null : factorToMediatype.firstEntry().getValue();
    }

    private static List<ImpMediaType> retrieveImpMediatypes(Imp imp) {
        final List<ImpMediaType> availableMediatypes = new ArrayList<>();

        if (imp.getAudio() != null) {
            availableMediatypes.add(ImpMediaType.audio);
        } else if (imp.getBanner() != null) {
            availableMediatypes.add(ImpMediaType.banner);
        } else if (imp.getXNative() != null) {
            availableMediatypes.add(ImpMediaType.xNative);
        } else if (imp.getVideo() != null) {
            if (imp.getVideo().getPlacement() != 1) {
                availableMediatypes.add(ImpMediaType.video_outstream);
            } else {
                availableMediatypes.add(ImpMediaType.video);
            }
        }

        return availableMediatypes;
    }

    private static BigDecimal resolveBidAdjustmentFactor(ExtRequestBidadjustmentfactors extBidadjustmentfactors,
                                                         ImpMediaType mediaType,
                                                         String bidder) {
        if (mediaType != null) {
            final Map<ImpMediaType, Map<String, BigDecimal>> mediatypes =
                    extBidadjustmentfactors.getMediatypes();
            final Map<String, BigDecimal> adjustmentsByMediatypes = mediatypes != null ? mediatypes.get(mediaType) : null;
            final BigDecimal adjustmentFactorByMediaType =
                    adjustmentsByMediatypes != null ? adjustmentsByMediatypes.get(bidder) : null;
            if (adjustmentFactorByMediaType != null) {
                return adjustmentFactorByMediaType;
            }
        }
        final BigDecimal adjustmentFactor = extBidadjustmentfactors.getAdjustments().get(bidder);

        return adjustmentFactor != null ? adjustmentFactor : BigDecimal.ONE;
    }
}
