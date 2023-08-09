package org.prebid.server.deals;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.deals.events.AdminEventService;
import org.prebid.server.deals.model.AdminCentralResponse;
import org.prebid.server.deals.model.AlertPriority;
import org.prebid.server.deals.model.DeploymentProperties;
import org.prebid.server.deals.model.PlannerProperties;
import org.prebid.server.deals.proto.CurrencyServiceState;
import org.prebid.server.deals.proto.RegisterRequest;
import org.prebid.server.deals.proto.Status;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.health.HealthMonitor;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.Initializable;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RegisterService implements Initializable, Suspendable {

    private static final Logger logger = LoggerFactory.getLogger(RegisterService.class);

    private static final DateTimeFormatter UTC_MILLIS_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .toFormatter();

    private static final String BASIC_AUTH_PATTERN = "Basic %s";
    private static final String PG_TRX_ID = "pg-trx-id";
    private static final String PBS_REGISTER_CLIENT_ERROR = "pbs-register-client-error";
    private static final String SERVICE_NAME = "register";

    private final PlannerProperties plannerProperties;
    private final DeploymentProperties deploymentProperties;
    private final AdminEventService adminEventService;
    private final DeliveryProgressService deliveryProgressService;
    private final AlertHttpService alertHttpService;
    private final HealthMonitor healthMonitor;
    private final CurrencyConversionService currencyConversionService;
    private final HttpClient httpClient;
    private final Vertx vertx;
    private final JacksonMapper mapper;

    private final long registerTimeout;
    private final long registerPeriod;
    private final String basicAuthHeader;
    private volatile long registerTimerId;
    private volatile boolean isSuspended;

    public RegisterService(PlannerProperties plannerProperties,
                           DeploymentProperties deploymentProperties,
                           AdminEventService adminEventService,
                           DeliveryProgressService deliveryProgressService,
                           AlertHttpService alertHttpService,
                           HealthMonitor healthMonitor,
                           CurrencyConversionService currencyConversionService,
                           HttpClient httpClient,
                           Vertx vertx,
                           JacksonMapper mapper) {
        this.plannerProperties = Objects.requireNonNull(plannerProperties);
        this.deploymentProperties = Objects.requireNonNull(deploymentProperties);
        this.adminEventService = Objects.requireNonNull(adminEventService);
        this.deliveryProgressService = Objects.requireNonNull(deliveryProgressService);
        this.alertHttpService = Objects.requireNonNull(alertHttpService);
        this.healthMonitor = Objects.requireNonNull(healthMonitor);
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.vertx = Objects.requireNonNull(vertx);
        this.mapper = Objects.requireNonNull(mapper);

        this.registerTimeout = plannerProperties.getTimeoutMs();
        this.registerPeriod = TimeUnit.SECONDS.toMillis(plannerProperties.getRegisterPeriodSeconds());
        this.basicAuthHeader = authHeader(plannerProperties.getUsername(), plannerProperties.getPassword());
    }

    /**
     * Creates Authorization header value from username and password.
     */
    private static String authHeader(String username, String password) {
        return BASIC_AUTH_PATTERN
                .formatted(Base64.getEncoder().encodeToString((username + ':' + password).getBytes()));
    }

    @Override
    public void suspend() {
        isSuspended = true;
        vertx.cancelTimer(registerTimerId);
    }

    @Override
    public void initialize() {
        registerTimerId = vertx.setPeriodic(registerPeriod, ignored -> performRegistration());
        performRegistration();
    }

    public void performRegistration() {
        register(headers());
    }

    protected void register(MultiMap headers) {
        if (isSuspended) {
            logger.warn("Register request was not sent to general planner, as planner service is suspended from"
                    + " register endpoint.");
            return;
        }

        final BigDecimal healthIndex = healthMonitor.calculateHealthIndex();
        final ZonedDateTime currencyLastUpdate = currencyConversionService.getLastUpdated();
        final RegisterRequest request = RegisterRequest.of(
                healthIndex,
                Status.of(currencyLastUpdate != null
                                ? CurrencyServiceState.of(UTC_MILLIS_FORMATTER.format(currencyLastUpdate))
                                : null,
                        deliveryProgressService.getOverallDeliveryProgressReport()),
                deploymentProperties.getPbsHostId(),
                deploymentProperties.getPbsRegion(),
                deploymentProperties.getPbsVendor());
        final String body = mapper.encodeToString(request);

        logger.info("Sending register request to Planner, {0} is {1}", PG_TRX_ID, headers.get(PG_TRX_ID));
        logger.debug("Register request payload: {0}", body);

        httpClient.post(plannerProperties.getRegisterEndpoint(), headers, body, registerTimeout)
                .onComplete(this::handleRegister);
    }

    protected MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.AUTHORIZATION_HEADER, basicAuthHeader)
                .add(PG_TRX_ID, UUID.randomUUID().toString());
    }

    private void handleRegister(AsyncResult<HttpClientResponse> asyncResult) {
        if (asyncResult.failed()) {
            final Throwable cause = asyncResult.cause();
            final String errorMessage = "Error occurred while registering with the Planner: " + cause;
            alert(errorMessage, logger::warn);
        } else {
            final HttpClientResponse response = asyncResult.result();
            final int statusCode = response.getStatusCode();
            final String responseBody = response.getBody();
            if (statusCode == HttpResponseStatus.OK.code()) {
                if (StringUtils.isNotBlank(responseBody)) {
                    adminEventService.publishAdminCentralEvent(parseRegisterResponse(responseBody));
                }
                alertHttpService.resetAlertCount(PBS_REGISTER_CLIENT_ERROR);
            } else {
                final String errorMessage = "Planner responded with non-successful code %s, response: %s"
                        .formatted(statusCode, responseBody);
                alert(errorMessage, logger::warn);
            }
        }
    }

    private AdminCentralResponse parseRegisterResponse(String responseBody) {
        try {
            return mapper.decodeValue(responseBody, AdminCentralResponse.class);
        } catch (DecodeException e) {
            final String errorMessage = "Cannot parse register response: " + responseBody;
            alert(errorMessage, logger::warn);
            throw new PreBidException(errorMessage, e);
        }
    }

    private void alert(String message, Consumer<String> logger) {
        alertHttpService.alertWithPeriod(SERVICE_NAME, PBS_REGISTER_CLIENT_ERROR, AlertPriority.MEDIUM, message);
        logger.accept(message);
    }
}
