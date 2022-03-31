package org.prebid.server.currency;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.currency.proto.CurrencyConversionRates;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestCurrency;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.spring.config.model.ExternalConversionProperties;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.Initializable;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Service for price currency conversion between currencies.
 */
public class CurrencyConversionService implements Initializable {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyConversionService.class);

    private static final String DEFAULT_BID_CURRENCY = "USD";
    // This number is chosen because of PriceGranularities default precision value of 2 + 1 for better accuracy
    private static final int DEFAULT_PRICE_PRECISION = 3;

    private final String currencyServerUrl;
    private final ExternalConversionProperties externalConversionProperties;
    private final JacksonMapper mapper;

    private Map<String, Map<String, BigDecimal>> externalCurrencyRates;
    private ZonedDateTime lastUpdated;

    public CurrencyConversionService(ExternalConversionProperties externalConversionProperties) {
        this.externalConversionProperties = externalConversionProperties;
        if (externalConversionProperties != null) {
            this.currencyServerUrl = HttpUtil.validateUrl(Objects.requireNonNull(
                    externalConversionProperties.getCurrencyServerUrl()));
            this.mapper = Objects.requireNonNull(externalConversionProperties.getMapper());
        } else {
            currencyServerUrl = null;
            mapper = null;
        }
    }

    /**
     * Sets timer for periodic currency rates updates and starts initial population.
     * <p>
     * Must be called on Vertx event loop thread.
     */
    @Override
    public void initialize() {
        if (externalConversionProperties != null) {
            final Long refreshPeriod = externalConversionProperties.getRefreshPeriodMs();
            final Long defaultTimeout = externalConversionProperties.getDefaultTimeoutMs();
            final HttpClient httpClient = externalConversionProperties.getHttpClient();

            final Vertx vertx = externalConversionProperties.getVertx();
            vertx.setPeriodic(refreshPeriod, ignored -> populatesLatestCurrencyRates(currencyServerUrl, defaultTimeout,
                    httpClient));
            populatesLatestCurrencyRates(currencyServerUrl, defaultTimeout, httpClient);

            externalConversionProperties.getMetrics().createCurrencyRatesGauge(this::isRatesStale);
        }
    }

    /**
     * Updates latest currency rates by making a call to currency server.
     */
    private void populatesLatestCurrencyRates(String currencyServerUrl, Long defaultTimeout, HttpClient httpClient) {
        httpClient.get(currencyServerUrl, defaultTimeout)
                .map(this::processResponse)
                .map(this::updateCurrencyRates)
                .otherwise(this::handleErrorResponse);
    }

    /**
     * Handles {@link HttpClientResponse}, analyzes response status
     * and creates {@link Future} with {@link CurrencyConversionRates} from body content
     * or throws {@link PreBidException} in case of errors.
     */
    private CurrencyConversionRates processResponse(HttpClientResponse response) {
        final int statusCode = response.getStatusCode();
        if (statusCode != 200) {
            throw new PreBidException(String.format("HTTP status code %d", statusCode));
        }

        final String body = response.getBody();
        try {
            return mapper.mapper().readValue(body, CurrencyConversionRates.class);
        } catch (IOException e) {
            throw new PreBidException(String.format("Cannot parse response: %s", body), e);
        }
    }

    private Void updateCurrencyRates(CurrencyConversionRates currencyConversionRates) {
        final Map<String, Map<String, BigDecimal>> receivedCurrencyRates = currencyConversionRates.getConversions();
        if (receivedCurrencyRates != null) {
            externalCurrencyRates = receivedCurrencyRates;
            lastUpdated = now();
        }

        return null;
    }

    /**
     * Handles errors occurred while HTTP request or response processing.
     */
    private Void handleErrorResponse(Throwable exception) {
        logger.warn("Error occurred while request to currency service", exception);

        if (externalRatesAreStale()) {
            externalCurrencyRates = null;
        }

        return null;
    }

    private boolean externalRatesAreStale() {
        final Long stalePeriodMs = externalConversionProperties.getStalePeriodMs();

        return stalePeriodMs != null && Duration.between(lastUpdated, now()).toMillis() > stalePeriodMs;
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now(externalConversionProperties.getClock());
    }

    public boolean isExternalRatesActive() {
        return externalConversionProperties != null;
    }

    public String getCurrencyServerUrl() {
        return currencyServerUrl;
    }

    public Long getRefreshPeriod() {
        return externalConversionProperties != null ? externalConversionProperties.getRefreshPeriodMs() : null;
    }

    public ZonedDateTime getLastUpdated() {
        return lastUpdated;
    }

    public Map<String, Map<String, BigDecimal>> getExternalCurrencyRates() {
        return externalCurrencyRates;
    }

    /**
     * Converts price from one currency to another using rates from {@link BidRequest} or external currency service.
     * If bidrequest.prebid.currecy.usepbsrates is true it takes rates from prebid server, if false from request.
     * Default value of usepbsrates is true.
     * Throws {@link PreBidException} in case conversion is not possible.
     */
    public BigDecimal convertCurrency(BigDecimal price, BidRequest bidRequest, String fromCurrency, String toCurrency) {
        return convertCurrency(price, currencyRates(bidRequest), fromCurrency, toCurrency, usepbsrates(bidRequest));
    }

    /**
     * Converts price from one currency to another using rates and usepbsrates flag defined in request.
     * If usepbsrates is true it takes rates from prebid server, if false from request. Default value of usepbsrates
     * is true.
     * Throws {@link PreBidException} in case conversion is not possible.
     */
    public BigDecimal convertCurrency(BigDecimal price,
                                      Map<String, Map<String, BigDecimal>> requestCurrencyRates,
                                      String fromCurrency,
                                      String toCurrency,
                                      Boolean usepbsrates) {
        // use Default USD currency if bidder left this field empty. After, when bidder will implement multi currency
        // support it will be changed to throwing PrebidException.
        final String effectiveFromCurrency = fromCurrency != null ? fromCurrency : DEFAULT_BID_CURRENCY;
        final String effectiveToCurrency = toCurrency != null ? toCurrency : DEFAULT_BID_CURRENCY;

        if (Objects.equals(effectiveToCurrency, effectiveFromCurrency)) {
            return price;
        }

        final Map<String, Map<String, BigDecimal>> firstPriorityRates;
        final Map<String, Map<String, BigDecimal>> secondPriorityRates;

        if (BooleanUtils.isFalse(usepbsrates)) {
            firstPriorityRates = requestCurrencyRates;
            secondPriorityRates = externalCurrencyRates;
        } else {
            firstPriorityRates = externalCurrencyRates;
            secondPriorityRates = requestCurrencyRates;
        }

        final BigDecimal conversionRate = getConversionRateByPriority(firstPriorityRates,
                secondPriorityRates,
                effectiveFromCurrency,
                effectiveToCurrency);

        if (conversionRate == null) {
            throw new PreBidException(
                    String.format("Unable to convert from currency %s to desired ad server currency %s",
                            effectiveFromCurrency, effectiveToCurrency));
        }

        return price.multiply(conversionRate).setScale(DEFAULT_PRICE_PRECISION, RoundingMode.HALF_EVEN);
    }

    private static Map<String, Map<String, BigDecimal>> currencyRates(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = extRequestPrebid(bidRequest);
        final ExtRequestCurrency currency = prebid != null ? prebid.getCurrency() : null;
        return currency != null ? currency.getRates() : null;
    }

    private static ExtRequestPrebid extRequestPrebid(BidRequest bidRequest) {
        final ExtRequest requestExt = bidRequest.getExt();
        return requestExt != null ? requestExt.getPrebid() : null;
    }

    private static Boolean usepbsrates(BidRequest bidRequest) {
        final ExtRequestPrebid prebid = extRequestPrebid(bidRequest);
        final ExtRequestCurrency currency = prebid != null ? prebid.getCurrency() : null;
        return currency != null ? currency.getUsepbsrates() : null;
    }

    /**
     * Returns conversion rate from the given currency rates according to priority.
     */
    private static BigDecimal getConversionRateByPriority(Map<String, Map<String, BigDecimal>> firstPriorityRates,
                                                          Map<String, Map<String, BigDecimal>> secondPriorityRates,
                                                          String fromCurrency,
                                                          String toCurrency) {

        return ObjectUtils.defaultIfNull(
                getConversionRate(firstPriorityRates, fromCurrency, toCurrency),
                getConversionRate(secondPriorityRates, fromCurrency, toCurrency));
    }

    /**
     * Looking for rates for a currency pair, using such approaches as straight, reverse and
     * intermediate rates.
     */
    private static BigDecimal getConversionRate(Map<String, Map<String, BigDecimal>> currencyConversionRates,
                                                String fromCurrency,
                                                String toCurrency) {
        if (MapUtils.isEmpty(currencyConversionRates)) {
            return null;
        }

        BigDecimal conversionRate;
        final Map<String, BigDecimal> directCurrencyRates = currencyConversionRates.get(fromCurrency);

        conversionRate = directCurrencyRates != null ? directCurrencyRates.get(toCurrency) : null;
        if (conversionRate != null) {
            return conversionRate;
        }

        final Map<String, BigDecimal> reverseCurrencyRates = currencyConversionRates.get(toCurrency);
        conversionRate = findReverseConversionRate(reverseCurrencyRates, fromCurrency);
        if (conversionRate != null) {
            return conversionRate;
        }

        return findIntermediateConversionRate(directCurrencyRates, reverseCurrencyRates);
    }

    /**
     * Finds reverse conversion rate.
     * If pair USD : EUR - 1.2 is present and EUR to USD conversion is needed, will return 1/1.2 conversion rate.
     */
    private static BigDecimal findReverseConversionRate(Map<String, BigDecimal> currencyRates,
                                                        String currency) {
        final BigDecimal reverseConversionRate = currencyRates != null
                ? currencyRates.get(currency)
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
    private static BigDecimal findIntermediateConversionRate(Map<String, BigDecimal> directCurrencyRates,
                                                             Map<String, BigDecimal> reverseCurrencyRates) {
        BigDecimal conversionRate = null;
        if (MapUtils.isNotEmpty(directCurrencyRates) && MapUtils.isNotEmpty(reverseCurrencyRates)) {
            final List<String> sharedCurrencies = new ArrayList<>(directCurrencyRates.keySet());
            sharedCurrencies.retainAll(reverseCurrencyRates.keySet());

            if (!sharedCurrencies.isEmpty()) {
                // pick any found shared currency
                final String sharedCurrency = sharedCurrencies.get(0);
                final BigDecimal directCurrencyRateIntermediate = directCurrencyRates.get(sharedCurrency);
                final BigDecimal reverseCurrencyRateIntermediate = reverseCurrencyRates.get(sharedCurrency);
                conversionRate = directCurrencyRateIntermediate.divide(reverseCurrencyRateIntermediate,
                        // chose largest precision among intermediate rates
                        reverseCurrencyRateIntermediate.compareTo(directCurrencyRateIntermediate) > 0
                                ? reverseCurrencyRateIntermediate.precision()
                                : directCurrencyRateIntermediate.precision(),
                        RoundingMode.HALF_EVEN);
            }
        }
        return conversionRate;
    }

    private boolean isRatesStale() {
        if (lastUpdated == null) {
            return false;
        }

        final ZonedDateTime stalenessBoundary = ZonedDateTime.now(externalConversionProperties.getClock())
                .minus(Duration.ofMillis(externalConversionProperties.getStaleAfterMs()));

        return lastUpdated.isBefore(stalenessBoundary);
    }
}
