package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;

/**
 * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity.ranges[i]
 */
@AllArgsConstructor(staticName = "of")
@Value
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
