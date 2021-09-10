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
import org.prebid.server.exception.PreBidException;
import org.prebid.server.util.HttpUtil;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Predicate;

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
        Action dealsAction;
        try {
            dealsAction = dealsActionFrom(routingContext);
        } catch (IllegalArgumentException e) {
            respondWithError(routingContext, HttpResponseStatus.BAD_REQUEST, e);
            return;
        }

        try {
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
            HttpUtil.executeSafely(routingContext, endpoint,
                    response -> response
                            .setStatusCode(HttpResponseStatus.NO_CONTENT.code())
                            .end());
        } catch (PreBidException e) {
            respondWithError(routingContext, HttpResponseStatus.INTERNAL_SERVER_ERROR, e);
        }
    }

    private Action dealsActionFrom(RoutingContext routingContext) {
        final String givenActionValue = routingContext.request().getParam(ACTION_NAME_PARAM);
        if (StringUtils.isEmpty(givenActionValue)) {
            throw new IllegalArgumentException(
                    String.format("Parameter '%s' is required and can't be empty", ACTION_NAME_PARAM));
        }

        final String[] possibleActions = Arrays.stream(Action.values()).map(Action::name).toArray(String[]::new);
        if (Arrays.stream(possibleActions).noneMatch(Predicate.isEqual(givenActionValue.toUpperCase()))) {
            throw new IllegalArgumentException(
                    String.format("Given '%s' parameter value is not among possible actions '%s'",
                            ACTION_NAME_PARAM, Arrays.toString(possibleActions)));
        }

        return Action.valueOf(givenActionValue.toUpperCase());
    }

    private void respondWithError(RoutingContext routingContext, HttpResponseStatus statusCode, Exception exception) {
        HttpUtil.executeSafely(routingContext, endpoint,
                response -> response
                        .setStatusCode(statusCode.code())
                        .end(exception.getMessage()));
    }

    enum Action {
        UPDATE_LINE_ITEMS, SEND_REPORT, REGISTER_INSTANCE, RESET_ALERT_COUNT, CREATE_REPORT, INVALIDATE_LINE_ITEMS
    }
}
