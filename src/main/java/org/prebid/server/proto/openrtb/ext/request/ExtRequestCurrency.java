package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Defines the contract for bidrequest.ext.prebid.currency
 */
@Value(staticConstructor = "of")
public class ExtRequestCurrency {

    /**
     * Defines the contract for bidrequest.ext.prebid.currency.rates
     */
    Map<String, Map<String, BigDecimal>> rates;

    /**
     * Defines the contract for bidrequest.ext.prebid.currency.usepbsrates
     */
    Boolean usepbsrates;
}
