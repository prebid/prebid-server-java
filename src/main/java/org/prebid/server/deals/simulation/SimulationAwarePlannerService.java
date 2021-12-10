package org.prebid.server.deals.simulation;

import io.vertx.core.AsyncResult;
import io.vertx.core.MultiMap;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.deals.AlertHttpService;
import org.prebid.server.deals.DeliveryProgressService;
import org.prebid.server.deals.PlannerService;
import org.prebid.server.deals.model.AlertPriority;
import org.prebid.server.deals.model.DeploymentProperties;
import org.prebid.server.deals.model.PlannerProperties;
import org.prebid.server.deals.proto.LineItemMetaData;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.http.HttpClient;

import java.time.Clock;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

public class SimulationAwarePlannerService extends PlannerService {

    private static final Logger logger = LoggerFactory.getLogger(SimulationAwarePlannerService.class);
    private static final DateTimeFormatter UTC_MILLIS_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .toFormatter();

    private static final String PG_SIM_TIMESTAMP = "pg-sim-timestamp";
    private static final String PBS_PLANNER_CLIENT_ERROR = "pbs-planner-client-error";

    private final SimulationAwareLineItemService lineItemService;
    private final Metrics metrics;
    private final AlertHttpService alertHttpService;

    private List<LineItemMetaData> lineItemMetaData;

    public SimulationAwarePlannerService(PlannerProperties plannerProperties,
                                         DeploymentProperties deploymentProperties,
                                         SimulationAwareLineItemService lineItemService,
                                         DeliveryProgressService deliveryProgressService,
                                         AlertHttpService alertHttpService,
                                         HttpClient httpClient,
                                         Metrics metrics,
                                         Clock clock,
                                         JacksonMapper mapper) {
        super(
                plannerProperties,
                deploymentProperties,
                lineItemService,
                deliveryProgressService,
                alertHttpService,
                httpClient,
                metrics,
                clock,
                mapper);

        this.lineItemService = Objects.requireNonNull(lineItemService);
        this.alertHttpService = Objects.requireNonNull(alertHttpService);
        this.metrics = Objects.requireNonNull(metrics);
        this.lineItemMetaData = new ArrayList<>();
    }

    public void advancePlans(ZonedDateTime now) {
        lineItemService.updateLineItems(lineItemMetaData, isPlannerResponsive.get(), now);
        lineItemService.advanceToNextPlan(now);
    }

    public void initiateLineItemsFetching(ZonedDateTime now) {
        fetchLineItemMetaData(planEndpoint, headers(now))
                .onComplete(this::handleInitializationResult);
    }

    /**
     * Handles result of initialization process. Sets metadata if request was successful.
     */
    @Override
    protected void handleInitializationResult(AsyncResult<List<LineItemMetaData>> plannerResponse) {
        if (plannerResponse.succeeded()) {
            metrics.updatePlannerRequestMetric(true);
            isPlannerResponsive.set(true);
            lineItemService.updateIsPlannerResponsive(true);
            lineItemMetaData = plannerResponse.result();
        } else {
            alert(plannerResponse.cause().getMessage(), AlertPriority.HIGH, logger::warn);
            logger.warn("Failed to retrieve line items from Planner after retry. Reason: {0}",
                    plannerResponse.cause().getMessage());
            isPlannerResponsive.set(false);
            lineItemService.updateIsPlannerResponsive(false);
            metrics.updatePlannerRequestMetric(false);
        }
    }

    private MultiMap headers(ZonedDateTime now) {
        return headers().add(PG_SIM_TIMESTAMP, UTC_MILLIS_FORMATTER.format(now));
    }

    private void alert(String message, AlertPriority alertPriority, Consumer<String> logger) {
        alertHttpService.alert(PBS_PLANNER_CLIENT_ERROR, alertPriority, message);
        logger.accept(message);
    }
}
