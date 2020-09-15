package org.prebid.server.currency;

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
import org.prebid.server.spring.config.model.ExternalConversionProperties;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.Initializable;
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
            final Long refreshPeriod = externalConversionProperties.getRefreshPeriod();
            final Long defaultTimeout = externalConversionProperties.getDefaultTimeout();
            final HttpClient httpClient = externalConversionProperties.getHttpClient();

            final Vertx vertx = externalConversionProperties.getVertx();
            vertx.setPeriodic(refreshPeriod, ignored -> populatesLatestCurrencyRates(currencyServerUrl, defaultTimeout,
                    httpClient));
            populatesLatestCurrencyRates(currencyServerUrl, defaultTimeout, httpClient);
        }
    }

    /**
     * Updates latest currency rates by making a call to currency server.
     */
    private void populatesLatestCurrencyRates(String currencyServerUrl, Long defaultTimeout, HttpClient httpClient) {
        httpClient.get(currencyServerUrl, defaultTimeout)
                .map(this::processResponse)
                .map(this::updateCurrencyRates)
                .recover(CurrencyConversionService::failResponse);
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

    private CurrencyConversionRates updateCurrencyRates(CurrencyConversionRates currencyConversionRates) {
        final Map<String, Map<String, BigDecimal>> receivedCurrencyRates = currencyConversionRates.getConversions();
        if (receivedCurrencyRates != null) {
            externalCurrencyRates = receivedCurrencyRates;
            lastUpdated = ZonedDateTime.now(Clock.systemUTC());
        }
        return currencyConversionRates;
    }

    /**
     * Handles errors occurred while HTTP request or response processing.
     */
    private static Future<CurrencyConversionRates> failResponse(Throwable exception) {
        logger.warn("Error occurred while request to currency service", exception);
        return Future.failedFuture(exception);
    }

    public ZonedDateTime getLastUpdated() {
        return lastUpdated;
    }

    /**
     * Converts price from bidCurrency to adServerCurrency using rates and usepbsrates flag defined in request.
     * If usepbsrates is true it takes rates from prebid server, if false from request. Default value of usepbsrates
     * is true.
     * Throws {@link PreBidException} in case conversion is not possible.
     */
    public BigDecimal convertCurrency(BigDecimal price, Map<String, Map<String, BigDecimal>> requestCurrencyRates,
                                      String adServerCurrency, String bidCurrency, Boolean usepbsrates) {
        // use Default USD currency if bidder left this field empty. After, when bidder will implement multi currency
        // support it will be changed to throwing PrebidException.
        final String effectiveBidCurrency = bidCurrency != null ? bidCurrency : DEFAULT_BID_CURRENCY;

        if (Objects.equals(adServerCurrency, effectiveBidCurrency)) {
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

        final BigDecimal conversionRate = getConversionRateByPriority(firstPriorityRates, secondPriorityRates,
                adServerCurrency, effectiveBidCurrency);

        if (conversionRate == null) {
            throw new PreBidException("no currency conversion available");
        }

        return price.divide(conversionRate, DEFAULT_PRICE_PRECISION, RoundingMode.HALF_EVEN);
    }

    /**
     * Returns conversion rate from the given currency rates according to priority.
     */
    private static BigDecimal getConversionRateByPriority(Map<String, Map<String, BigDecimal>> firstPriorityRates,
                                                          Map<String, Map<String, BigDecimal>> secondPriorityRates,
                                                          String adServerCurrency,
                                                          String effectiveBidCurrency) {

        return ObjectUtils.defaultIfNull(
                getConversionRate(firstPriorityRates, adServerCurrency, effectiveBidCurrency),
                getConversionRate(secondPriorityRates, adServerCurrency, effectiveBidCurrency));
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
