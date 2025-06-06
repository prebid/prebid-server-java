package org.prebid.server.bidadjustments;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

public class FloorAdjustmentFactorResolver {

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
                .map(type -> type == ImpMediaType.video_instream ? ImpMediaType.video : type)
                .map(adjustmentFactorsByMediaTypes::get)
                .map(bidderToFactor -> MapUtils.isNotEmpty(bidderToFactor)
                        ? bidderToFactor.entrySet().stream()
                        .filter(entry -> StringUtils.equalsIgnoreCase(entry.getKey(), bidder))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null)
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
