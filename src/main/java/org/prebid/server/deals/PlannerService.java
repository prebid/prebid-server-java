package org.prebid.server.deals;

import com.fasterxml.jackson.core.type.TypeReference;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.deals.model.AlertPriority;
import org.prebid.server.deals.model.DeploymentProperties;
import org.prebid.server.deals.model.PlannerProperties;
import org.prebid.server.deals.proto.LineItemMetaData;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.time.Clock;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class manages line item metadata retrieving from planner and reporting.
 */
public class PlannerService implements Suspendable {

    private static final Logger logger = LoggerFactory.getLogger(PlannerService.class);

    protected static final TypeReference<List<LineItemMetaData>> LINE_ITEM_METADATA_TYPE_REFERENCE
            = new TypeReference<List<LineItemMetaData>>() {
            };

    private static final String BASIC_AUTH_PATTERN = "Basic %s";
    private static final String PG_TRX_ID = "pg-trx-id";
    private static final String INSTANCE_ID_PARAMETER = "instanceId";
    private static final String REGION_PARAMETER = "region";
    private static final String VENDOR_PARAMETER = "vendor";
    private static final String SERVICE_NAME = "planner";
    private static final String PBS_PLANNER_CLIENT_ERROR = "pbs-planner-client-error";
    private static final String PBS_PLANNER_EMPTY_RESPONSE = "pbs-planner-empty-response-error";

    private final LineItemService lineItemService;
    private final DeliveryProgressService deliveryProgressService;
    private final AlertHttpService alertHttpService;
    protected final HttpClient httpClient;
    private final Metrics metrics;
    private final Clock clock;
    private final JacksonMapper mapper;

    protected final String planEndpoint;
    private final long plannerTimeout;
    private final String basicAuthHeader;

    protected final AtomicBoolean isPlannerResponsive;
    private volatile boolean isSuspended;

    public PlannerService(PlannerProperties plannerProperties,
                          DeploymentProperties deploymentProperties,
                          LineItemService lineItemService,
                          DeliveryProgressService deliveryProgressService,
                          AlertHttpService alertHttpService,
                          HttpClient httpClient,
                          Metrics metrics,
                          Clock clock,
                          JacksonMapper mapper) {
        this.lineItemService = Objects.requireNonNull(lineItemService);
        this.deliveryProgressService = Objects.requireNonNull(deliveryProgressService);
        this.alertHttpService = Objects.requireNonNull(alertHttpService);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.metrics = Objects.requireNonNull(metrics);
        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);

        this.planEndpoint = buildPlannerMetaDataUrl(plannerProperties.getPlanEndpoint(),
                deploymentProperties.getPbsHostId(),
                deploymentProperties.getPbsRegion(),
                deploymentProperties.getPbsVendor());
        this.plannerTimeout = plannerProperties.getTimeoutMs();
        this.basicAuthHeader = authHeader(plannerProperties.getUsername(), plannerProperties.getPassword());

        this.isPlannerResponsive = new AtomicBoolean(true);
    }

    @Override
    public void suspend() {
        isSuspended = true;
    }

    /**
     * Fetches line items meta data from Planner
     */
    protected Future<List<LineItemMetaData>> fetchLineItemMetaData(String plannerUrl, MultiMap headers) {
        logger.info("Requesting line items metadata and plans from Planner, {0} is {1}", PG_TRX_ID,
                headers.get(PG_TRX_ID));
        final long startTime = clock.millis();
        return httpClient.get(plannerUrl, headers, plannerTimeout)
                .map(httpClientResponse -> processLineItemMetaDataResponse(httpClientResponse, startTime));
    }

    protected MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.AUTHORIZATION_HEADER, basicAuthHeader)
                .add(PG_TRX_ID, UUID.randomUUID().toString());
    }

    /**
     * Processes response from planner.
     * If status code == 4xx - stop fetching process.
     * If status code =! 2xx - start retry fetching process.
     * If status code == 200 - parse response.
     */
    protected List<LineItemMetaData> processLineItemMetaDataResponse(HttpClientResponse response, long startTime) {
        final int statusCode = response.getStatusCode();
        if (statusCode != 200) {
            throw new PreBidException(String.format("Failed to fetch data from Planner, HTTP status code %d",
                    statusCode));
        }

        final String body = response.getBody();
        if (body == null) {
            throw new PreBidException("Failed to fetch data from planner, response can't be null");
        }

        metrics.updateRequestTimeMetric(MetricName.planner_request_time, clock.millis() - startTime);

        logger.debug("Received line item metadata and plans from Planner: {0}", body);

        try {
            final List<LineItemMetaData> lineItemMetaData = mapper.decodeValue(body,
                    LINE_ITEM_METADATA_TYPE_REFERENCE);
            validateForEmptyResponse(lineItemMetaData);
            metrics.updateLineItemsNumberMetric(lineItemMetaData.size());
            logger.info("Received line item metadata from Planner, amount: {0}", lineItemMetaData.size());

            return lineItemMetaData;
        } catch (DecodeException e) {
            final String errorMessage = String.format("Cannot parse response: %s", body);
            throw new PreBidException(errorMessage, e);
        }
    }

    private void validateForEmptyResponse(List<LineItemMetaData> lineItemMetaData) {
        if (CollectionUtils.isEmpty(lineItemMetaData)) {
            alertHttpService.alertWithPeriod(SERVICE_NAME, PBS_PLANNER_EMPTY_RESPONSE, AlertPriority.LOW,
                    "Response without line items was received from planner");
        } else {
            alertHttpService.resetAlertCount(PBS_PLANNER_EMPTY_RESPONSE);
        }
    }

    /**
     * Creates Authorization header value from username and password.
     */
    private static String authHeader(String username, String password) {
        return String.format(BASIC_AUTH_PATTERN, Base64.getEncoder().encodeToString((username + ':' + password)
                .getBytes()));
    }

    /**
     * Builds url for fetching metadata from planner
     */
    private static String buildPlannerMetaDataUrl(String plannerMetaDataUrl, String pbsHostname, String pbsRegion,
                                                  String pbsVendor) {
        return String.format("%s?%s=%s&%s=%s&%s=%s", plannerMetaDataUrl, INSTANCE_ID_PARAMETER, pbsHostname,
                REGION_PARAMETER, pbsRegion, VENDOR_PARAMETER, pbsVendor);
    }

    /**
     * Fetches line item metadata from planner during the regular, not retry flow.
     */
    public void updateLineItemMetaData() {
        if (isSuspended) {
            logger.warn("Fetch request was not sent to general planner, as planner service is suspended from"
                    + " register endpoint.");
            return;
        }

        final MultiMap headers = headers();
        fetchLineItemMetaData(planEndpoint, headers)
                .recover(ignored -> startRecoveryProcess(planEndpoint, headers))
                .setHandler(this::handleInitializationResult);
    }

    private Future<List<LineItemMetaData>> startRecoveryProcess(String planEndpoint, MultiMap headers) {
        metrics.updatePlannerRequestMetric(false);
        logger.info("Retry to fetch line items from general planner by uri = {0}", planEndpoint);

        return fetchLineItemMetaData(planEndpoint, headers);
    }

    /**
     * Handles result of initialization process. Sets metadata if request was successful.
     */
    protected void handleInitializationResult(AsyncResult<List<LineItemMetaData>> plannerResponse) {
        if (plannerResponse.succeeded()) {
            handleSuccessInitialization(plannerResponse);
        } else {
            handleFailedInitialization(plannerResponse);
        }
    }

    private void handleSuccessInitialization(AsyncResult<List<LineItemMetaData>> plannerResponse) {
        alertHttpService.resetAlertCount(PBS_PLANNER_CLIENT_ERROR);
        metrics.updatePlannerRequestMetric(true);
        isPlannerResponsive.set(true);
        lineItemService.updateIsPlannerResponsive(true);
        updateMetaData(plannerResponse.result());
    }

    private void handleFailedInitialization(AsyncResult<List<LineItemMetaData>> plannerResponse) {
        final String message = String.format("Failed to retrieve line items from GP. Reason: %s",
                plannerResponse.cause().getMessage());
        alertHttpService.alertWithPeriod(SERVICE_NAME, PBS_PLANNER_CLIENT_ERROR, AlertPriority.MEDIUM, message);
        logger.warn(message);
        isPlannerResponsive.set(false);
        lineItemService.updateIsPlannerResponsive(false);
        metrics.updatePlannerRequestMetric(false);
    }

    /**
     * Overwrites maps with metadata
     */
    private void updateMetaData(List<LineItemMetaData> metaData) {
        lineItemService.updateLineItems(metaData, isPlannerResponsive.get());
        deliveryProgressService.processDeliveryProgressUpdateEvent();
    }
}
