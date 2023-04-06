package org.prebid.server.auction;

import org.apache.commons.collections4.MapUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class AdjustmentFactorResolver {

    public BigDecimal resolve(ImpMediaType impMediaType,
                              ExtRequestBidAdjustmentFactors adjustmentFactors,
                              String bidder) {

        final Set<ImpMediaType> impMediaTypes = impMediaType != null
                ? EnumSet.of(impMediaType)
                : Collections.emptySet();

        return resolve(impMediaTypes, adjustmentFactors, bidder);
    }

    public BigDecimal resolve(Set<ImpMediaType> impMediaTypes,
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

        final BigDecimal mediaTypeMinFactor = impMediaTypes.stream()
                .map(adjustmentFactorsByMediaTypes::get)
                .map(bidderToFactor -> MapUtils.isNotEmpty(bidderToFactor)
                        ? bidderToFactor.get(bidder)
                        : effectiveBidderAdjustmentFactor)
                .filter(Objects::nonNull)
                .min(Comparator.comparing(Function.identity()))
                .orElse(null);

        return createFactorValue(mediaTypeMinFactor, bidderAdjustmentFactor);
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
