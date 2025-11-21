package org.prebid.server.currency.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Represents Currency Server response containing currency conversion rates for specific date.
 */
@Value(staticConstructor = "of")
public class CurrencyConversionRates {

    @JsonProperty("dataAsOf")
    String dataAsOf;

    Map<String, Map<String, BigDecimal>> conversions;
}
