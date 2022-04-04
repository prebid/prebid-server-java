package org.prebid.server.functional.model.mock.services.currencyconversion

import org.prebid.server.functional.model.ResponseModel

import java.time.ZoneId
import java.time.ZonedDateTime

import static java.time.ZoneOffset.UTC

class CurrencyConversionRatesResponse implements ResponseModel {

    String dataAsOf
    Map<String, Map<String, BigDecimal>> conversions

    static CurrencyConversionRatesResponse getDefaultCurrencyConversionRatesResponse(Map<String, Map<String, BigDecimal>> conversionRates = defaultConversionRates) {
        new CurrencyConversionRatesResponse().tap {
            dataAsOf = ZonedDateTime.now(ZoneId.from(UTC)).minusDays(1) as String
            conversions = conversionRates
        }
    }

    static Map<String, Map<String, BigDecimal>> getDefaultConversionRates() {
        ["USD": ["EUR": 0.8872327211427558],
         "EUR": ["USD": 1.3429368029739777]]
    }
}
