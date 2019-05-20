package org.prebid.server.currency;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.MapUtils;
import org.prebid.server.currency.proto.CurrencyConversionRates;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.ZonedDateTime;
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
    // This number is chosen because of PriceGranularities default precision value of 2 + 1 for better accuracy
    private static final int DEFAULT_PRICE_PRECISION = 3;

    private final String currencyServerUrl;
    private final long defaultTimeout;
    private final long refreshPeriod;
    private final Vertx vertx;
    private final HttpClient httpClient;

    private Map<String, Map<String, BigDecimal>> latestCurrencyRates;
    private ZonedDateTime lastUpdated;

    public CurrencyConversionService(String currencyServerUrl, long defaultTimeout, long refreshPeriod, Vertx vertx,
                                     HttpClient httpClient) {
        this.currencyServerUrl = HttpUtil.validateUrl(Objects.requireNonNull(currencyServerUrl));
        this.defaultTimeout = defaultTimeout;
        this.refreshPeriod = validateRefreshPeriod(refreshPeriod);
        this.vertx = Objects.requireNonNull(vertx);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    public ZonedDateTime getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Sets timer for periodic currency rates updates and starts initial population.
     * <p>
     * Must be called on Vertx event loop thread.
     */
    public void initialize() {
        vertx.setPeriodic(refreshPeriod, ignored -> populatesLatestCurrencyRates());
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
        httpClient.get(currencyServerUrl, defaultTimeout)
                .compose(CurrencyConversionService::processResponse)
                .compose(this::updateCurrencyRates)
                .recover(CurrencyConversionService::failResponse);
    }

    /**
     * Handles {@link HttpClientResponse}, analyzes response status
     * and creates {@link Future} with {@link CurrencyConversionRates} from body content
     * or throws {@link PreBidException} in case of errors.
     */
    private static Future<CurrencyConversionRates> processResponse(HttpClientResponse response) {
        final int statusCode = response.getStatusCode();
        if (statusCode != 200) {
            throw new PreBidException(String.format("HTTP status code %d", statusCode));
        }

        final String body = response.getBody();
        final CurrencyConversionRates currencyConversionRates;
        try {
            currencyConversionRates = Json.mapper.readValue(body, CurrencyConversionRates.class);
        } catch (IOException e) {
            throw new PreBidException(String.format("Cannot parse response: %s", body), e);
        }

        return Future.succeededFuture(currencyConversionRates);
    }

    private Future<CurrencyConversionRates> updateCurrencyRates(CurrencyConversionRates currencyConversionRates) {
        final Map<String, Map<String, BigDecimal>> receivedCurrencyRates = currencyConversionRates.getConversions();
        if (receivedCurrencyRates != null) {
            latestCurrencyRates = receivedCurrencyRates;
            lastUpdated = ZonedDateTime.now(Clock.systemUTC());
        }
        return Future.succeededFuture(currencyConversionRates);
    }

    /**
     * Handles errors occurred while HTTP request or response processing.
     */
    private static Future<CurrencyConversionRates> failResponse(Throwable exception) {
        logger.warn("Error occurred while request to currency service", exception);
        return Future.failedFuture(exception);
    }

    /**
     * Converts price from bidCurrency to adServerCurrency using rates defined in request or if absent, from
     * latest currency rates. Throws {@link PreBidException} in case conversion is not possible.
     */
    public BigDecimal convertCurrency(BigDecimal price, Map<String, Map<String, BigDecimal>> requestCurrencyRates,
                                      String adServerCurrency, String bidCurrency) {
        // use Default USD currency if bidder left this field empty. After, when bidder will implement multi currency
        // support it will be changed to throwing PrebidException.
        final String effectiveBidCurrency = bidCurrency != null ? bidCurrency : DEFAULT_BID_CURRENCY;

        if (Objects.equals(adServerCurrency, effectiveBidCurrency)) {
            return price;
        }

        // get conversion rate from request currency rates if it is present
        BigDecimal conversionRate = null;
        if (requestCurrencyRates != null) {
            conversionRate = getConversionRate(requestCurrencyRates, adServerCurrency, effectiveBidCurrency);
        }

        // if conversion rate from requestCurrency was not found, try the same from latest currencies
        if (conversionRate == null) {
            conversionRate = getConversionRate(latestCurrencyRates, adServerCurrency, effectiveBidCurrency);
        }

        if (conversionRate == null) {
            throw new PreBidException("no currency conversion available");
        }

        return price.divide(conversionRate, DEFAULT_PRICE_PRECISION, RoundingMode.HALF_EVEN);
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
                RoundingMode.HALF_EVEN)
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
                        RoundingMode.HALF_EVEN);
            }
        }
        return conversionRate;
    }
}
