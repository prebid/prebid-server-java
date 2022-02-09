package org.prebid.server.floors;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidadjustmentfactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.util.ObjectUtil;

import java.math.BigDecimal;
import java.util.Map;

public class BasicPriceFloorAdjuster implements PriceFloorAdjuster {

    @Override
    public BigDecimal adjustForImp(Imp imp, String bidder, BidRequest bidRequest) {
        final ExtRequestBidadjustmentfactors extBidadjustmentfactors =
                extractBidadjustmentfactors(bidRequest);

        if (extBidadjustmentfactors == null) {
            return imp.getBidfloor();
        }

        // TODO: check with Bret 34 answer
        final ImpMediaType mediaType = null;

        return resolveBidAdjustmentFactor(extBidadjustmentfactors, mediaType, bidder);
    }

    private static ExtRequestBidadjustmentfactors extractBidadjustmentfactors(BidRequest bidRequest) {
        final ExtRequest extRequest = bidRequest.getExt();
        final ExtRequestPrebid extPrebid = ObjectUtil.getIfNotNull(extRequest, ExtRequest::getPrebid);
        return ObjectUtil.getIfNotNull(extPrebid, ExtRequestPrebid::getBidadjustmentfactors);
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
