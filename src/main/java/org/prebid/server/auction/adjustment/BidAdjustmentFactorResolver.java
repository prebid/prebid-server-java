package org.prebid.server.auction.adjustment;

import org.apache.commons.collections4.MapUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class BidAdjustmentFactorResolver {

    public BigDecimal resolve(ImpMediaType impMediaType,
                              ExtRequestBidAdjustmentFactors adjustmentFactors,
                              String bidder) {

        final EnumMap<ImpMediaType, Map<String, BigDecimal>> adjustmentFactorsByMediaTypes =
                adjustmentFactors.getMediatypes();

        final BigDecimal effectiveBidderAdjustmentFactor = Optional.ofNullable(adjustmentFactors.getAdjustments())
                .map(factors -> factors.get(bidder))
                .orElse(BigDecimal.ONE);

        if (MapUtils.isEmpty(adjustmentFactorsByMediaTypes)) {
            return effectiveBidderAdjustmentFactor;
        }

        return Optional.ofNullable(impMediaType)
                .map(adjustmentFactorsByMediaTypes::get)
                .map(factors -> factors.get(bidder))
                .orElse(effectiveBidderAdjustmentFactor);
    }
}
