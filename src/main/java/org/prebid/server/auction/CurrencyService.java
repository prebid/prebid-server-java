package org.prebid.server.auction;

import org.apache.commons.collections4.MapUtils;
import org.prebid.server.auction.model.Currency;
import org.prebid.server.exception.PreBidException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service for price currency conversion between currencies.
 */
public class CurrencyService {

    private static final Integer SCALE = 5;
    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final LatestRatesService latestRatesService;

    public CurrencyService(LatestRatesService latestRatesService) {
        this.latestRatesService = latestRatesService;
    }

    /**
     * Converts price from bidCurrency to adServerCurrency using rates defined in request or if absent, from
     * latest service currency. Throws {@link PreBidException} in case conversion is not possible.
     */
    public BigDecimal convertCurrency(BigDecimal price, Currency requestCurrencyRates, String adServerCurrency,
                                      String bidCurrency) {
        if (Objects.equals(adServerCurrency, bidCurrency)) {
            return price;
        }

        // use Default USD currency if bidder left this field empty. After, when bidder will implement multi currency
        // support it will be changed to throwing PerbidException.
        if (bidCurrency == null) {
            bidCurrency = DEFAULT_BID_CURRENCY;
        }

        // get conversion rate from request currency rates if it is present
        BigDecimal conversionRate = null;
        if (requestCurrencyRates != null) {
            conversionRate = getConversionRate(requestCurrencyRates.getConversions(), adServerCurrency, bidCurrency);
        }

        // if conversion rate from requestCurrency was not found, try the same from latest currencies
        if (conversionRate == null) {
            final Map<String, Map<String, BigDecimal>> latestRates = latestRatesService.getRates();

            if (latestRates != null) {
                conversionRate = getConversionRate(latestRates, adServerCurrency, bidCurrency);
            }
        }

        if (conversionRate == null) {
            throw new PreBidException("no currency conversion available");
        }

        return price.divide(conversionRate, SCALE, BigDecimal.ROUND_FLOOR);
    }

    /**
     * Looking for rates for adServerCurrency - bidCurrency pair, using such approaches as straight, reverse and
     * intermediate rates.
     */
    private static BigDecimal getConversionRate(Map<String, Map<String, BigDecimal>> conversions,
                                                String adServerCurrency, String bidCurrency) {
        BigDecimal conversionRate;
        final Map<String, BigDecimal> serverCurrencyRates = conversions.get(adServerCurrency);
        final Map<String, BigDecimal> bidCurrencyRates = conversions.get(bidCurrency);

        if (MapUtils.isEmpty(conversions)) {
            return null;
        }

        conversionRate = serverCurrencyRates != null ? serverCurrencyRates.get(bidCurrency) : null;
        if (conversionRate != null) {
            return conversionRate;
        }

        conversionRate = findReverseConversionRate(bidCurrencyRates, adServerCurrency);
        if (conversionRate != null) {
            return conversionRate;
        }

        return findIntermediateConversionRate(serverCurrencyRates, bidCurrencyRates);
    }

    /**
     * Finds reverse conversion rate.
     * If pair USD : EUR - 1.2 is present and EUR to USD conversion is needed, will return 1/1.2 conversion rate.
     */
    private static BigDecimal findReverseConversionRate(Map<String, BigDecimal> bidCurrencyRates,
                                                        String adServerCurrency) {
        BigDecimal reverseConversionRate = bidCurrencyRates != null ? bidCurrencyRates.get(adServerCurrency) : null;

        return reverseConversionRate != null
                ? BigDecimal.ONE.divide(reverseConversionRate, SCALE, BigDecimal.ROUND_FLOOR)
                : null;
    }

    /**
     * Finds intermediate conversion rate.
     * If pairs USD : AUD - 1.2 and EUR : AUD - 1.5 are present, and EUR to USD conversion is needed, will return
     * (1/1.5) * 1.2 conversion rate.
     */
    private static BigDecimal findIntermediateConversionRate(Map<String, BigDecimal> adServerCurrencyRates,
                                                             Map<String, BigDecimal> bidCurrencyRates) {
        BigDecimal conversionRate = null;
        if (MapUtils.isNotEmpty(adServerCurrencyRates) && MapUtils.isNotEmpty(bidCurrencyRates)) {
            final List<String> sharedCurrencies = new ArrayList<>(adServerCurrencyRates.keySet());
            sharedCurrencies.retainAll(bidCurrencyRates.keySet());

            if (!sharedCurrencies.isEmpty()) {
                // pick any found shared currency
                final String sharedCurrency = sharedCurrencies.get(0);

                conversionRate = adServerCurrencyRates.get(sharedCurrency).multiply(
                        BigDecimal.ONE.divide(bidCurrencyRates.get(sharedCurrency), SCALE,
                                BigDecimal.ROUND_FLOOR));
            }
        }
        return conversionRate;
    }
}
