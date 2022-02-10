package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.MapUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidadjustmentfactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.util.BidderUtil;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

        final ImpMediaType mediaType = chooseMediaType(extBidadjustmentfactors, bidder);

        final BigDecimal factor = mediaType != null
                ? resolveBidAdjustmentFactor(extBidadjustmentfactors, mediaType, bidder)
                : BigDecimal.ONE;

        //Scale needs to be set, either way BigDecimal cannot be passed forward
        return imp.getBidfloor() != null
                ? BidderUtil.roundFloor(imp.getBidfloor().divide(factor, 30, RoundingMode.HALF_EVEN))
                : imp.getBidfloor();
    }

    private static ExtRequestBidadjustmentfactors extractBidadjustmentfactors(BidRequest bidRequest) {
        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid extPrebid = ObjectUtil.getIfNotNull(extRequest, ExtRequest::getPrebid);
        return ObjectUtil.getIfNotNull(extPrebid, ExtRequestPrebid::getBidadjustmentfactors);
    }

    private static ImpMediaType chooseMediaType(ExtRequestBidadjustmentfactors extBidadjustmentfactors, String bidder) {
        final TreeMap<BigDecimal, ImpMediaType> factorToMediatype = new TreeMap<>();

        for (ImpMediaType mediaType : extBidadjustmentfactors.getAvailableMediaTypes()) {
            Map<String, BigDecimal> mediaTypeValues = extBidadjustmentfactors.getMediatypes().get(mediaType);
            final BigDecimal factor = mediaTypeValues.get(bidder);
            if (factor == null) {
                continue;
            }
            factorToMediatype.put(factor.setScale(4, RoundingMode.UNNECESSARY), mediaType);
        }

        return MapUtils.isEmpty(factorToMediatype) ? null : factorToMediatype.firstEntry().getValue();
    }

    private static BigDecimal resolveBidAdjustmentFactor(ExtRequestBidadjustmentfactors extBidadjustmentfactors,
                                                         ImpMediaType mediaType,
                                                         String bidder) {
        final Map<ImpMediaType, Map<String, BigDecimal>> mediatypes =
                extBidadjustmentfactors.getMediatypes();
        final Map<String, BigDecimal> adjustmentsByMediatypes = mediatypes != null ? mediatypes.get(mediaType) : null;
        final BigDecimal adjustmentFactorByMediaType =
                adjustmentsByMediatypes != null ? adjustmentsByMediatypes.get(bidder) : null;
        if (adjustmentFactorByMediaType != null) {
            return adjustmentFactorByMediaType;
        }
        return extBidadjustmentfactors.getAdjustments().get(bidder);
    }
}
