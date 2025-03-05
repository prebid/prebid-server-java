package org.prebid.server.auction.adjustment;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class BidAdjustmentFactorResolver {

    public BigDecimal resolve(ImpMediaType impMediaType,
                              ExtRequestBidAdjustmentFactors adjustmentFactors,
                              String bidder,
                              String seat) {

        final EnumMap<ImpMediaType, Map<String, BigDecimal>> adjustmentFactorsByMediaTypes =
                adjustmentFactors.getMediatypes();
        final Map<String, BigDecimal> adjustmentsFactors = adjustmentFactors.getAdjustments();

        return resolveFromMediaTypes(impMediaType, seat, adjustmentFactorsByMediaTypes)
                .or(() -> resolveFromAdjustments(seat, adjustmentsFactors))
                .or(() -> resolveFromMediaTypes(impMediaType, bidder, adjustmentFactorsByMediaTypes))
                .or(() -> resolveFromAdjustments(bidder, adjustmentsFactors))
                .orElse(BigDecimal.ONE);
    }

    private static Optional<BigDecimal> resolveFromMediaTypes(
            ImpMediaType mediaType,
            String bidderCode,
            EnumMap<ImpMediaType, Map<String, BigDecimal>> adjustmentFactors) {

        if (MapUtils.isEmpty(adjustmentFactors)) {
            return Optional.empty();
        }

        return Optional.ofNullable(mediaType)
                .map(adjustmentFactors::get)
                .flatMap(factors -> factors.entrySet().stream()
                        .filter(entry -> StringUtils.equalsIgnoreCase(entry.getKey(), bidderCode))
                        .map(Map.Entry::getValue)
                        .findFirst());
    }

    private static Optional<BigDecimal> resolveFromAdjustments(String bidderCode,
                                                               Map<String, BigDecimal> adjustmentFactors) {

        return Optional.ofNullable(adjustmentFactors)
                .map(factors -> factors.get(bidderCode));
    }
}
