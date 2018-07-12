package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.JsonNode;
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
    JsonNode pricegranularity;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.currency
     */
    ExtCurrency currency;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.includewinners
     */
    Boolean includewinners;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.includebidderkeys
     */
    Boolean includebidderkeys;
}
