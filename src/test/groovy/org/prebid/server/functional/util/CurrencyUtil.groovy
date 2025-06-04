package org.prebid.server.functional.util

import org.prebid.server.functional.model.Currency
import org.prebid.server.functional.model.response.currencyrates.CurrencyRatesResponse

import java.math.RoundingMode

import static org.prebid.server.functional.model.Currency.CAD
import static org.prebid.server.functional.model.Currency.CHF
import static org.prebid.server.functional.model.Currency.EUR
import static org.prebid.server.functional.model.Currency.GBP
import static org.prebid.server.functional.model.Currency.JPY
import static org.prebid.server.functional.model.Currency.USD

class CurrencyUtil {

    public static final Currency DEFAULT_CURRENCY = USD
    public static final Map<Currency, Map<Currency, BigDecimal>> DEFAULT_CURRENCY_RATES = [(USD): [(EUR): 0.9249838127832763,
                                                                                                   (GBP): 0.793776804452961,
                                                                                                   (EUR): 0.9249838127832763,
                                                                                                   (CHF): 0.9033391915641477,
                                                                                                   (JPY): 151.1886041994265,
                                                                                                   (CAD): 1.357136250115623],
                                                                                           (GBP): [(USD): 1.2597999770088517,
                                                                                                   (EUR): 1.1495574203931487],
                                                                                           (EUR): [(USD): 1.3429368029739777]]
    public static final int PRICE_PRECISION = 3
    public static final int CURRENCY_CONVERSION_PRECISION = 3

    static BigDecimal getPriceAfterCurrencyConversion(BigDecimal value,
                                                      Currency from,
                                                      Currency to,
                                                      CurrencyRatesResponse currencyRatesResponse) {
        (value * currencyRatesResponse.rates[from.value][to.value])
                .setScale(CURRENCY_CONVERSION_PRECISION, RoundingMode.HALF_EVEN)
    }

    static BigDecimal convertCurrency(BigDecimal price,
                                      Currency fromCurrency,
                                      Currency toCurrency,
                                      Map<Currency, Map<Currency, BigDecimal>> rates = DEFAULT_CURRENCY_RATES) {
        return (price * getConversionRate(fromCurrency, toCurrency, rates)).setScale(PRICE_PRECISION, RoundingMode.HALF_EVEN)
    }

    private static BigDecimal getConversionRate(Currency fromCurrency,
                                                Currency toCurrency,
                                                Map<Currency, Map<Currency, BigDecimal>> rates = DEFAULT_CURRENCY_RATES) {
        def conversionRate
        if (fromCurrency == toCurrency) {
            conversionRate = 1
        } else if (toCurrency in DEFAULT_CURRENCY_RATES?[fromCurrency]) {
            conversionRate = DEFAULT_CURRENCY_RATES[fromCurrency][toCurrency]
        } else if (fromCurrency in DEFAULT_CURRENCY_RATES?[toCurrency]) {
            conversionRate = 1 / DEFAULT_CURRENCY_RATES[toCurrency][fromCurrency]
        } else {
            conversionRate = getCrossConversionRate(fromCurrency, toCurrency, rates)
        }
        conversionRate
    }

    private static BigDecimal getCrossConversionRate(Currency fromCurrency,
                                                     Currency toCurrency,
                                                     Map<Currency, Map<Currency, BigDecimal>> rates) {

        for (Map<Currency, BigDecimal> rate : rates.values()) {
            def fromRate = rate?[fromCurrency]
            def toRate = rate?[toCurrency]
            if (fromRate && toRate) {
                return toRate / fromRate
            }
        }
        null
    }
}
