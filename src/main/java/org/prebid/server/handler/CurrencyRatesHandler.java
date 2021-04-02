package org.prebid.server.handler;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Handles HTTP request for latest currency rates update information.
 */
public class CurrencyRatesHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(CurrencyRatesHandler.class);

    private final CurrencyConversionService currencyConversionService;
    private final JacksonMapper mapper;

    public CurrencyRatesHandler(CurrencyConversionService currencyConversionService, JacksonMapper mapper) {
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void handle(RoutingContext context) {
        try {
            context.response().headers().add(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON);
            context.response()
                    .end(mapper.mapper().writeValueAsString(
                            Response.of(
                                    currencyConversionService.isExternalRatesActive(),
                                    currencyConversionService.getCurrencyServerUrl(),
                                    toNanos(currencyConversionService.getRefreshPeriod()),
                                    currencyConversionService.getLastUpdated(),
                                    currencyConversionService.getExternalCurrencyRates())));
        } catch (IOException e) {
            logger.error("Critical error when marshaling latest currency rates update response", e);
            context.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
        }
    }

    private static Long toNanos(Long refreshPeriod) {
        return refreshPeriod != null ? TimeUnit.MILLISECONDS.toNanos(refreshPeriod) : null;
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class Response {

        boolean active;

        String source;

        @JsonProperty("fetchingIntervalNs")
        Long fetchingIntervalNs;

        @JsonProperty("lastUpdated")
        ZonedDateTime lastUpdated;

        Map<String, Map<String, BigDecimal>> rates;
    }
}
