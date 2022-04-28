package org.prebid.server.functional.model.mock.services.currencyconversion

import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.ResponseModel

import java.time.ZoneId
import java.time.ZonedDateTime

import static java.time.ZoneOffset.UTC
import static org.prebid.server.functional.model.Currency.EUR
import static org.prebid.server.functional.model.Currency.USD

class CurrencyConversionRatesResponse implements ResponseModel {

    String dataAsOf
    Map<Currency, Map<Currency, BigDecimal>> conversions

    static CurrencyConversionRatesResponse getDefaultCurrencyConversionRatesResponse(Map<Currency, Map<Currency, BigDecimal>> conversionRates = defaultConversionRates) {
        new CurrencyConversionRatesResponse().tap {
            dataAsOf = ZonedDateTime.now(ZoneId.from(UTC)).minusDays(1) as String
            conversions = conversionRates
        }
    }

    static Map<Currency, Map<Currency, BigDecimal>> getDefaultConversionRates() {
        [(USD): [(EUR): 0.8872327211427558],
         (EUR): [(USD): 1.3429368029739777]]
    }
}
