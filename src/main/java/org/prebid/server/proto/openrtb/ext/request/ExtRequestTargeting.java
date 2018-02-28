package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.targeting
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestTargeting {

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.pricegranularity
     */
    String pricegranularity;
}
