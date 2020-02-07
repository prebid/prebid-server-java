package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.json.JacksonMapper;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Objects;

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
        final ZonedDateTime lastUpdated = currencyConversionService.getLastUpdated();
        final String lastUpdatedString = lastUpdated != null ? lastUpdated.toString() : "no value";
        try {
            context.response().end(mapper.mapper().writeValueAsString(Response.of(lastUpdatedString)));
        } catch (IOException e) {
            logger.error("Critical error when marshaling latest currency rates update response", e);
            context.response().setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code()).end();
        }
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static class Response {

        String lastUpdate;
    }
}
