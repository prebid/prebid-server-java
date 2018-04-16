package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity.[i]
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtPriceGranularityBucket {

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity.[i].precision
     */
    Integer precision;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity.[i].min
     */
    BigDecimal min;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity.[i].max
     */
    BigDecimal max;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity.[i].increment
     */
    BigDecimal increment;
}
