package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.auction.PriceGranularity;

import java.util.List;

/**
 * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtPriceGranularity {

    public static ExtPriceGranularity from(PriceGranularity priceGranularity) {
        return ExtPriceGranularity.of(priceGranularity.getPrecision(), priceGranularity.getRanges());
    }

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity.precision
     */
    Integer precision;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity.ranges
     */
    List<ExtGranularityRange> ranges;
}
