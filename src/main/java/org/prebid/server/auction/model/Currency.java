package org.prebid.server.auction.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Defines the contract for bidrequest.ext.prebid.targeting.currency
 */
@AllArgsConstructor(staticName = "of")
@Value
public class Currency {

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.dataAsOf
     */
    @JsonProperty("dataAsOf")
    String dataAsOf;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting.conversions
     */
    Map<String, Map<String, BigDecimal>> conversions;
}
