package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.deals.DeliveryProgressService;
import org.prebid.server.deals.proto.report.LineItemStatusReport;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;

import java.time.ZonedDateTime;
import java.util.Objects;

public class LineItemStatusHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(LineItemStatusHandler.class);

    private static final String ID_PARAM = "id";
    private static final String PG_SIM_TIMESTAMP = "pg-sim-timestamp";

    private final DeliveryProgressService deliveryProgressService;
    private final JacksonMapper mapper;
    private final String endpoint;

    public LineItemStatusHandler(DeliveryProgressService deliveryProgressService, JacksonMapper mapper,
                                 String endpoint) {
        this.deliveryProgressService = Objects.requireNonNull(deliveryProgressService);
        this.mapper = Objects.requireNonNull(mapper);
        this.endpoint = Objects.requireNonNull(endpoint);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        routingContext.response()
                .exceptionHandler(LineItemStatusHandler::handleResponseException);

        final String lineItemId = lineItemIdFrom(routingContext);
        if (StringUtils.isEmpty(lineItemId)) {
            HttpUtil.executeSafely(routingContext, endpoint,
                    response -> response
                            .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                            .end(String.format("%s parameter is required", ID_PARAM)));
            return;
        }

        try {
            final ZonedDateTime time = HttpUtil.getDateFromHeader(routingContext.request().headers(), PG_SIM_TIMESTAMP);
            final LineItemStatusReport report = deliveryProgressService.getLineItemStatusReport(lineItemId, time);

            HttpUtil.headers().forEach(entry -> routingContext.response().putHeader(entry.getKey(), entry.getValue()));
            HttpUtil.executeSafely(routingContext, endpoint,
                    response -> response
                            .setStatusCode(HttpResponseStatus.OK.code())
                            .end(mapper.encodeToString(report)));
        } catch (PreBidException e) {
            HttpUtil.executeSafely(routingContext, endpoint,
                    response -> response
                            .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                            .end(e.getMessage()));
        } catch (Exception e) {
            HttpUtil.executeSafely(routingContext, endpoint,
                    response -> response
                            .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                            .end(e.getMessage()));
        }
    }

    private static String lineItemIdFrom(RoutingContext routingContext) {
        return routingContext.request().getParam(ID_PARAM);
    }

    private static void handleResponseException(Throwable exception) {
        logger.warn("Failed to send line item status response: {0}", exception.getMessage());
    }
}
