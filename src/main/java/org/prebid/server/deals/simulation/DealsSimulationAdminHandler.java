package org.prebid.server.deals.simulation;

import com.fasterxml.jackson.core.type.TypeReference;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;

import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DealsSimulationAdminHandler implements Handler<RoutingContext> {

    private static final TypeReference<Map<String, Double>> BID_RATES_TYPE_REFERENCE
            = new TypeReference<Map<String, Double>>() {
            };

    private static final Logger logger = LoggerFactory.getLogger(DealsSimulationAdminHandler.class);

    private static final Pattern URL_SUFFIX_PATTERN = Pattern.compile("/pbs-admin/e2eAdmin(.*)");
    private static final String PLANNER_REGISTER_PATH = "/planner/register";
    private static final String PLANNER_FETCH_PATH = "/planner/fetchLineItems";
    private static final String ADVANCE_PLAN_PATH = "/advancePlans";
    private static final String REPORT_PATH = "/dealstats/report";
    private static final String BID_RATE_PATH = "/bidRate";
    private static final String PG_SIM_TIMESTAMP = "pg-sim-timestamp";

    private final SimulationAwareRegisterService registerService;
    private final SimulationAwarePlannerService plannerService;
    private final SimulationAwareDeliveryProgressService deliveryProgressService;
    private final SimulationAwareDeliveryStatsService deliveryStatsService;
    private final SimulationAwareHttpBidderRequester httpBidderRequester;
    private final JacksonMapper mapper;
    private final String endpoint;

    public DealsSimulationAdminHandler(
            SimulationAwareRegisterService registerService,
            SimulationAwarePlannerService plannerService,
            SimulationAwareDeliveryProgressService deliveryProgressService,
            SimulationAwareDeliveryStatsService deliveryStatsService,
            SimulationAwareHttpBidderRequester httpBidderRequester,
            JacksonMapper mapper,
            String endpoint) {

        this.registerService = Objects.requireNonNull(registerService);
        this.plannerService = Objects.requireNonNull(plannerService);
        this.deliveryProgressService = Objects.requireNonNull(deliveryProgressService);
        this.deliveryStatsService = Objects.requireNonNull(deliveryStatsService);
        this.httpBidderRequester = httpBidderRequester;
        this.mapper = Objects.requireNonNull(mapper);
        this.endpoint = Objects.requireNonNull(endpoint);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final HttpServerRequest request = routingContext.request();
        final Matcher matcher = URL_SUFFIX_PATTERN.matcher(request.uri());

        if (!matcher.find() || StringUtils.isBlank(matcher.group(1))) {
            HttpUtil.executeSafely(routingContext, endpoint,
                    response -> response
                            .setStatusCode(HttpResponseStatus.NOT_FOUND.code())
                            .end("Requested url was not found"));
            return;
        }

        try {
            final String endpointPath = matcher.group(1);
            final ZonedDateTime now = getPgSimDate(endpointPath, request.headers());
            handleEndpoint(routingContext, endpointPath, now);

            HttpUtil.executeSafely(routingContext, endpoint,
                    response -> response
                            .setStatusCode(HttpResponseStatus.OK.code())
                            .end());
        } catch (InvalidRequestException e) {
            logger.error(e.getMessage(), e);
            respondWith(routingContext, HttpResponseStatus.BAD_REQUEST, e.getMessage());
        } catch (NotFoundException e) {
            logger.error(e.getMessage(), e);
            respondWith(routingContext, HttpResponseStatus.NOT_FOUND, e.getMessage());
        } catch (Exception e) {
            logger.error(e.getMessage(), e);
            respondWith(routingContext, HttpResponseStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private ZonedDateTime getPgSimDate(String endpointPath, MultiMap headers) {
        ZonedDateTime now = null;
        if (!endpointPath.equals(BID_RATE_PATH)) {
            now = HttpUtil.getDateFromHeader(headers, PG_SIM_TIMESTAMP);
            if (now == null) {
                throw new InvalidRequestException(String.format(
                        "pg-sim-timestamp with simulated current date is required for endpoints: %s, %s, %s, %s",
                        PLANNER_REGISTER_PATH, PLANNER_FETCH_PATH, ADVANCE_PLAN_PATH, REPORT_PATH));
            }
        }
        return now;
    }

    private void handleEndpoint(RoutingContext routingContext, String endpointPath, ZonedDateTime now) {
        if (endpointPath.startsWith(PLANNER_REGISTER_PATH)) {
            registerService.performRegistration(now);

        } else if (endpointPath.startsWith(PLANNER_FETCH_PATH)) {
            plannerService.initiateLineItemsFetching(now);

        } else if (endpointPath.startsWith(ADVANCE_PLAN_PATH)) {
            plannerService.advancePlans(now);

        } else if (endpointPath.startsWith(REPORT_PATH)) {
            deliveryProgressService.createDeliveryProgressReport(now);
            deliveryStatsService.sendDeliveryProgressReports(now);

        } else if (endpointPath.startsWith(BID_RATE_PATH)) {
            if (httpBidderRequester != null) {
                handleBidRatesEndpoint(routingContext);
            } else {
                throw new InvalidRequestException(String.format("Calling %s is not make sense since "
                        + "Prebid Server configured to use real bidder exchanges in simulation mode", BID_RATE_PATH));
            }
        } else {
            throw new NotFoundException(String.format("Requested url %s was not found", endpointPath));
        }
    }

    private void handleBidRatesEndpoint(RoutingContext routingContext) {
        final Buffer body = routingContext.getBody();
        if (body == null) {
            throw new InvalidRequestException(String.format("Body is required for %s endpoint", BID_RATE_PATH));
        }

        try {
            httpBidderRequester.setBidRates(mapper.decodeValue(body, BID_RATES_TYPE_REFERENCE));
        } catch (DecodeException e) {
            throw new InvalidRequestException(String.format("Failed to parse bid rates body: %s", e.getMessage()));
        }
    }

    private void respondWith(RoutingContext routingContext, HttpResponseStatus status, String body) {
        HttpUtil.executeSafely(routingContext, endpoint,
                response -> response
                        .setStatusCode(status.code())
                        .end(body));
    }

    private static class NotFoundException extends RuntimeException {
        NotFoundException(String message) {
            super(message);
        }
    }
}
