package org.prebid.server.auction;

import io.vertx.core.http.HttpClient;
import io.vertx.core.json.Json;
import org.prebid.server.auction.model.Currency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

/**
 * Class holding and updating latest currencies from server.
 */
public class LatestRatesService {

    private static final String CURRENCY_SERVER_URL = "currency.prebid.org";
    private static final Logger logger = LoggerFactory.getLogger(LatestRatesService.class);

    private HttpClient httpClient;
    private volatile Currency currency;

    public LatestRatesService(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    /**
     * Updates rate from latest currency server.
     */
    public void updateRates() {
        httpClient.getNow(80, CURRENCY_SERVER_URL, "/latest.json",
                httpClientResponse -> httpClientResponse.bodyHandler(
                        buffer -> {
                            final String body = buffer.toString();
                            try {
                                currency = Json.mapper.readValue(body, Currency.class);
                            } catch (IOException e) {
                                logger.warn("Error occurred during parsing response from latest currency service");
                            }
                        }));
    }

    /**
     * Returns rates from latest currency server.
     */
    public Map<String, Map<String, BigDecimal>> getRates() {
        return currency != null ? currency.getConversions() : null;
    }
}
