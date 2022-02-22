package org.prebid.server.deals.simulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

public class DealsSimulationAdminHandlerTest extends VertxTest {

    @Rule
    public MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private SimulationAwareRegisterService registerService;

    @Mock
    private SimulationAwarePlannerService plannerService;

    @Mock
    private SimulationAwareDeliveryProgressService deliveryProgressService;

    @Mock
    private SimulationAwareDeliveryStatsService deliveryStatsService;

    @Mock
    private SimulationAwareHttpBidderRequester httpBidderRequester;

    @Mock
    private RoutingContext routingContext;

    @Mock
    private HttpServerRequest request;

    @Mock
    private HttpServerResponse response;

    private ZonedDateTime now;

    private DealsSimulationAdminHandler dealsSimulationAdminHandler;

    @Before
    public void setUp() {
        now = ZonedDateTime.now(Clock.fixed(Instant.parse("2019-10-10T00:00:00Z"), ZoneOffset.UTC));
        dealsSimulationAdminHandler = new DealsSimulationAdminHandler(registerService,
                plannerService, deliveryProgressService, deliveryStatsService, httpBidderRequester, jacksonMapper,
                "endpoint");
        given(routingContext.request()).willReturn(request);
        given(routingContext.response()).willReturn(response);
        given(request.headers()).willReturn(MultiMap.caseInsensitiveMultiMap()
                .add("pg-sim-timestamp", now.toString()));
        given(response.setStatusCode(anyInt())).willReturn(response);
    }

    @Test
    public void handleShouldCallPerformRegistrationHandler() {
        // given
        given(request.uri()).willReturn("/pbs-admin/e2eAdmin/planner/register");

        // when
        dealsSimulationAdminHandler.handle(routingContext);

        // then
        verify(registerService).performRegistration(eq(now));
    }

    @Test
    public void handleShouldCallFetchLineItemHandler() {
        // given
        given(request.uri()).willReturn("/pbs-admin/e2eAdmin/planner/fetchLineItems");

        // when
        dealsSimulationAdminHandler.handle(routingContext);

        // then
        verify(plannerService).initiateLineItemsFetching(eq(now));
    }

    @Test
    public void handleShouldCallAdvancePlanHandler() {
        // given
        given(request.uri()).willReturn("/pbs-admin/e2eAdmin/advancePlans");

        // when
        dealsSimulationAdminHandler.handle(routingContext);

        // then
        verify(plannerService).advancePlans(eq(now));
    }

    @Test
    public void handleShouldCallReportHandler() {
        // given
        given(request.uri()).willReturn("/pbs-admin/e2eAdmin/dealstats/report");

        // when
        dealsSimulationAdminHandler.handle(routingContext);

        // then
        verify(deliveryProgressService).createDeliveryProgressReport(eq(now));
        verify(deliveryStatsService).sendDeliveryProgressReports(eq(now));
    }

    @Test
    public void handleShouldCallSetBidRateHandler() throws JsonProcessingException {
        // given
        given(request.uri()).willReturn("/pbs-admin/e2eAdmin/bidRate");
        given(routingContext.getBody()).willReturn(Buffer.buffer(
                mapper.writeValueAsString(Collections.singletonMap("lineItemId", 1.00))));

        // when
        dealsSimulationAdminHandler.handle(routingContext);

        // then
        verify(httpBidderRequester).setBidRates(any());
    }

    @Test
    public void handleShouldRespondWithErrorWhenBidderRequesterIsNotSet() {
        // given
        dealsSimulationAdminHandler = new DealsSimulationAdminHandler(
                registerService, plannerService, deliveryProgressService, deliveryStatsService, null, jacksonMapper,
                "endpoint");
        given(request.uri()).willReturn("/pbs-admin/e2eAdmin/bidRate");

        // when
        dealsSimulationAdminHandler.handle(routingContext);

        // then
        verify(response).setStatusCode(400);
        verify(response).end(eq("Calling /bidRate is not make sense since "
                + "Prebid Server configured to use real bidder exchanges in simulation mode"));
    }

    @Test
    public void handleShouldRespondWithNotFoundWhenHandlerNotFound() {
        // given
        given(request.uri()).willReturn("/pbs-admin/e2eAdmin/invalid");

        // when
        dealsSimulationAdminHandler.handle(routingContext);

        // then
        verify(response).setStatusCode(404);
        verify(response).end(eq("Requested url /invalid was not found"));
    }

    @Test
    public void handleShouldRespondWithBadRequestWhenRequiredDateHeaderNotFound() {
        // given
        given(request.uri()).willReturn("/pbs-admin/e2eAdmin/dealstats/startreport");
        given(request.headers()).willReturn(MultiMap.caseInsensitiveMultiMap());

        // when
        dealsSimulationAdminHandler.handle(routingContext);

        // then
        verify(response).setStatusCode(400);
        verify(response).end(eq("pg-sim-timestamp with simulated current date is required for endpoints:"
                + " /planner/register, /planner/fetchLineItems, /advancePlans, /dealstats/report"));
    }

    @Test
    public void handleShouldRespondWithBadRequestWhenBodyNotFoundForSetBidRatesHandler() {
        // given
        given(request.uri()).willReturn("/pbs-admin/e2eAdmin/bidRate");
        given(routingContext.getBody()).willReturn(null);

        // when
        dealsSimulationAdminHandler.handle(routingContext);

        // then
        verify(response).setStatusCode(400);
        verify(response).end(eq("Body is required for /bidRate endpoint"));
    }

    @Test
    public void handleShouldRespondWithBadRequestWhenBodyHasIncorrectFormatForSetBidRatesHandler() {
        // given
        given(request.uri()).willReturn("/pbs-admin/e2eAdmin/bidRate");
        given(routingContext.getBody()).willReturn(Buffer.buffer("{"));

        // when
        dealsSimulationAdminHandler.handle(routingContext);

        // then
        verify(response).setStatusCode(400);
        verify(response).end(startsWith("Failed to parse bid rates body"));
    }

    @Test
    public void handleShouldRespondWithInternalServerErrorStatus() {
        // given
        given(request.uri()).willReturn("/pbs-admin/e2eAdmin/advancePlans");
        doThrow(new PreBidException("Error")).when(plannerService).advancePlans(any());

        // when
        dealsSimulationAdminHandler.handle(routingContext);

        // then
        verify(response).setStatusCode(eq(500));
        verify(response).end(eq("Error"));
    }
}
