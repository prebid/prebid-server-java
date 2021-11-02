package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.deals.AlertHttpService;
import org.prebid.server.deals.DeliveryProgressService;
import org.prebid.server.deals.DeliveryStatsService;
import org.prebid.server.deals.LineItemService;
import org.prebid.server.deals.PlannerService;
import org.prebid.server.deals.RegisterService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.util.HttpUtil;

import java.time.ZonedDateTime;
import java.util.Objects;

public class ForceDealsUpdateHandler implements Handler<RoutingContext> {

    private static final String ACTION_NAME_PARAM = "action_name";

    private final DeliveryStatsService deliveryStatsService;
    private final PlannerService plannerService;
    private final RegisterService registerService;
    private final AlertHttpService alertHttpService;
    private final DeliveryProgressService deliveryProgressService;
    private final LineItemService lineItemService;
    private final String endpoint;

    public ForceDealsUpdateHandler(DeliveryStatsService deliveryStatsService,
                                   PlannerService plannerService,
                                   RegisterService registerService,
                                   AlertHttpService alertHttpService,
                                   DeliveryProgressService deliveryProgressService,
                                   LineItemService lineItemService,
                                   String endpoint) {

        this.deliveryStatsService = Objects.requireNonNull(deliveryStatsService);
        this.plannerService = Objects.requireNonNull(plannerService);
        this.registerService = Objects.requireNonNull(registerService);
        this.alertHttpService = Objects.requireNonNull(alertHttpService);
        this.deliveryProgressService = Objects.requireNonNull(deliveryProgressService);
        this.lineItemService = Objects.requireNonNull(lineItemService);
        this.endpoint = Objects.requireNonNull(endpoint);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        try {
            handleDealsAction(dealsActionFrom(routingContext));
            HttpUtil.executeSafely(routingContext, endpoint,
                    response -> response
                            .setStatusCode(HttpResponseStatus.NO_CONTENT.code())
                            .end());
        } catch (InvalidRequestException e) {
            respondWithError(routingContext, HttpResponseStatus.BAD_REQUEST, e);
        } catch (Exception e) {
            respondWithError(routingContext, HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private static DealsAction dealsActionFrom(RoutingContext routingContext) {
        final String actionName = routingContext.request().getParam(ACTION_NAME_PARAM);
        if (StringUtils.isEmpty(actionName)) {
            throw new InvalidRequestException(String.format(
                    "Parameter '%s' is required and can't be empty",
                    ACTION_NAME_PARAM));
        }

        try {
            return DealsAction.valueOf(actionName.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            throw new InvalidRequestException(String.format(
                    "Given '%s' parameter value '%s' is not among possible actions",
                    ACTION_NAME_PARAM,
                    actionName));
        }
    }

    private void handleDealsAction(DealsAction dealsAction) {
        switch (dealsAction) {
            case UPDATE_LINE_ITEMS:
                plannerService.updateLineItemMetaData();
                break;
            case SEND_REPORT:
                deliveryStatsService.sendDeliveryProgressReports();
                break;
            case REGISTER_INSTANCE:
                registerService.performRegistration();
                break;
            case RESET_ALERT_COUNT:
                alertHttpService.resetAlertCount("pbs-register-client-error");
                alertHttpService.resetAlertCount("pbs-planner-client-error");
                alertHttpService.resetAlertCount("pbs-planner-empty-response-error");
                alertHttpService.resetAlertCount("pbs-delivery-stats-client-error");
                break;
            case CREATE_REPORT:
                deliveryProgressService.createDeliveryProgressReports(ZonedDateTime.now());
                break;
            case INVALIDATE_LINE_ITEMS:
                lineItemService.invalidateLineItems();
                break;
            default:
                throw new IllegalStateException("Unexpected action value");
        }
    }

    private void respondWithError(RoutingContext routingContext, HttpResponseStatus statusCode, Exception exception) {
        HttpUtil.executeSafely(routingContext, endpoint,
                response -> response
                        .setStatusCode(statusCode.code())
                        .end(exception.getMessage()));
    }

    enum DealsAction {
        UPDATE_LINE_ITEMS, SEND_REPORT, REGISTER_INSTANCE, RESET_ALERT_COUNT, CREATE_REPORT, INVALIDATE_LINE_ITEMS
    }
}
