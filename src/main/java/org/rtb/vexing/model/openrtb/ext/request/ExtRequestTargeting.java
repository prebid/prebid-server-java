package org.rtb.vexing.model.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.targeting
 */
@Value
@AllArgsConstructor(staticName = "of")
public class ExtRequestTargeting {

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity
     */
    String pricegranularity;
}
