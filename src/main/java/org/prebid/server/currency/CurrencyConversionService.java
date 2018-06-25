package org.prebid.server.currency;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.MapUtils;
import org.prebid.server.currency.proto.CurrencyConversionRates;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.util.HttpUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service for price currency conversion between currencies.
 */
public class CurrencyConversionService {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyConversionService.class);

    private static final String DEFAULT_BID_CURRENCY = "USD";

    private final String currencyServerUrl;
    private final long refreshPeriod;
    private final Vertx vertx;
    private final HttpClient httpClient;

    private Map<String, Map<String, BigDecimal>> latestCurrencyRates = null;

    public CurrencyConversionService(String currencyServerUrl, long refreshPeriod, Vertx vertx, HttpClient httpClient) {
        this.currencyServerUrl = HttpUtil.validateUrl(Objects.requireNonNull(currencyServerUrl));
        this.refreshPeriod = validateRefreshPeriod(refreshPeriod);
        this.vertx = Objects.requireNonNull(vertx);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    /**
     * Sets timer for periodic currency rates updates and starts initial population.
     * <p>
     * Must be called on Vertx event loop thread.
     */
    public void initialize() {
        vertx.setPeriodic(refreshPeriod, aLong -> populatesLatestCurrencyRates());
        populatesLatestCurrencyRates();
    }

    /**
     * Validates consumed refresh period value.
     */
    private long validateRefreshPeriod(long refreshPeriod) {
        if (refreshPeriod < 1) {
            throw new IllegalArgumentException("Refresh period for updating rates must be positive value");
        }
        return refreshPeriod;
    }

    /**
     * Updates latest currency rates by making a call to currency server.
     */
    private void populatesLatestCurrencyRates() {
        httpClient.getAbs(currencyServerUrl, this::handleResponse)
                .exceptionHandler(CurrencyConversionService::handleException)
                .end();
    }

    private void handleResponse(HttpClientResponse response) {
        final int statusCode = response.statusCode();
        if (statusCode != 200) {
            logger.warn("Currency server response code is {0}", statusCode);
            return;
        }
        response.bodyHandler(this::handleBody)
                .exceptionHandler(CurrencyConversionService::handleException);
    }

    /**
     * Parses body content and populates latest currency rates.
     */
    private void handleBody(Buffer buffer) {
        try {
            final Map<String, Map<String, BigDecimal>> receivedCurrencyRates =
                    Json.mapper.readValue(buffer.toString(), CurrencyConversionRates.class).getConversions();
            if (receivedCurrencyRates != null) {
                latestCurrencyRates = receivedCurrencyRates;
            }
        } catch (IOException e) {
            logger.warn("Error occurred during parsing response from latest currency service", e);
        }
    }

    /**
     * Handles an error occurred while request. In our case adds error log.
     */
    private static void handleException(Throwable exception) {
        logger.warn("Error occurred while request to currency service", exception);
    }

    /**
     * Converts price from bidCurrency to adServerCurrency using rates defined in request or if absent, from
     * latest currency rates. Throws {@link PreBidException} in case conversion is not possible.
     */
    public BigDecimal convertCurrency(BigDecimal price,
                                      Map<String, Map<String, BigDecimal>> requestCurrencyRates,
                                      String adServerCurrency,
                                      String bidCurrency) {
        // use Default USD currency if bidder left this field empty. After, when bidder will implement multi currency
        // support it will be changed to throwing PrebidException.
        if (bidCurrency == null) {
            bidCurrency = DEFAULT_BID_CURRENCY;
        }

        if (Objects.equals(adServerCurrency, bidCurrency)) {
            return price;
        }

        // get conversion rate from request currency rates if it is present
        BigDecimal conversionRate = null;
        if (requestCurrencyRates != null) {
            conversionRate = getConversionRate(requestCurrencyRates, adServerCurrency, bidCurrency);
        }

        // if conversion rate from requestCurrency was not found, try the same from latest currencies
        if (conversionRate == null) {
            conversionRate = getConversionRate(latestCurrencyRates, adServerCurrency, bidCurrency);
        }

        if (conversionRate == null) {
            throw new PreBidException("no currency conversion available");
        }

        return price.divide(conversionRate, conversionRate.precision(), BigDecimal.ROUND_HALF_EVEN);
    }

    /**
     * Looking for rates for adServerCurrency - bidCurrency pair, using such approaches as straight, reverse and
     * intermediate rates.
     */
    private static BigDecimal getConversionRate(Map<String, Map<String, BigDecimal>> currencyConversionRates,
                                                String adServerCurrency, String bidCurrency) {
        if (MapUtils.isEmpty(currencyConversionRates)) {
            return null;
        }

        BigDecimal conversionRate;
        final Map<String, BigDecimal> serverCurrencyRates = currencyConversionRates.get(adServerCurrency);

        conversionRate = serverCurrencyRates != null ? serverCurrencyRates.get(bidCurrency) : null;
        if (conversionRate != null) {
            return conversionRate;
        }

        final Map<String, BigDecimal> bidCurrencyRates = currencyConversionRates.get(bidCurrency);
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
        final BigDecimal reverseConversionRate = bidCurrencyRates != null
                ? bidCurrencyRates.get(adServerCurrency)
                : null;

        return reverseConversionRate != null
                ? BigDecimal.ONE.divide(reverseConversionRate, reverseConversionRate.precision(),
                BigDecimal.ROUND_HALF_EVEN)
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
                final BigDecimal adServerCurrencyRateIntermediate = adServerCurrencyRates.get(sharedCurrency);
                final BigDecimal bidCurrencyRateIntermediate = bidCurrencyRates.get(sharedCurrency);
                conversionRate = adServerCurrencyRateIntermediate.divide(bidCurrencyRateIntermediate,
                        // chose largest precision among intermediate rates
                        bidCurrencyRateIntermediate.compareTo(adServerCurrencyRateIntermediate) > 0
                                ? bidCurrencyRateIntermediate.precision()
                                : adServerCurrencyRateIntermediate.precision(),
                        BigDecimal.ROUND_HALF_EVEN);
            }
        }
        return conversionRate;
    }
}
