package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

import java.math.BigDecimal;

/**
 * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity.ranges[i]
 */
@Value(staticConstructor = "of")
public class ExtGranularityRange {

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity.ranges[i].max
     */
    BigDecimal max;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity.ranges[i].increment
     */
    BigDecimal increment;
}
