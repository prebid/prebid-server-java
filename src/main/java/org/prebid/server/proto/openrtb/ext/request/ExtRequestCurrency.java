package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Defines the contract for bidrequest.ext.prebid.targeting.currency
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestCurrency {
    /**
     * Defines the contract for bidrequest.ext.prebid.currency.rates
     */
    Map<String, Map<String, BigDecimal>> rates;
}
