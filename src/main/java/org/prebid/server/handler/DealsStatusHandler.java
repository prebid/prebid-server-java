package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.deals.DeliveryProgressService;
import org.prebid.server.deals.proto.report.DeliveryProgressReport;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;

public class DealsStatusHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(DealsStatusHandler.class);

    private final DeliveryProgressService deliveryProgressService;
    private final JacksonMapper mapper;

    public DealsStatusHandler(DeliveryProgressService deliveryProgressService, JacksonMapper mapper) {
        this.deliveryProgressService = Objects.requireNonNull(deliveryProgressService);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final DeliveryProgressReport deliveryProgressReport = deliveryProgressService
                .getOverallDeliveryProgressReport();
        final String body = mapper.encode(deliveryProgressReport);

        // don't send the response if client has gone
        if (routingContext.response().closed()) {
            logger.warn("The client already closed connection, response will be skipped");
            return;
        }

        routingContext.response()
                .putHeader(HttpUtil.CONTENT_TYPE_HEADER, HttpHeaderValues.APPLICATION_JSON)
                .exceptionHandler(this::handleResponseException)
                .end(body);
    }

    private void handleResponseException(Throwable throwable) {
        logger.warn("Failed to send deals status response: {0}", throwable.getMessage());
    }
}
