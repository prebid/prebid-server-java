package org.prebid.server.deals.simulation;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.deals.AlertHttpService;
import org.prebid.server.deals.DeliveryProgressService;
import org.prebid.server.deals.model.DeploymentProperties;
import org.prebid.server.deals.model.PlannerProperties;
import org.prebid.server.deals.proto.LineItemMetaData;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class SimulationAwarePlannerServiceTest extends VertxTest {

    private static final String PLAN_ENDPOINT = "plan-endpoint";
    private static final String REGISTER_ENDPOINT = "register-endpoint";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String PBS_HOST = "pbs-host";
    private static final String PBS_REGION = "pbs-region";
    private static final String PBS_VENDOR = "pbs-vendor";

    @Rule
    public final MockitoRule rule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;
    @Mock
    private SimulationAwareLineItemService lineItemService;
    @Mock
    private DeliveryProgressService deliveryProgressService;
    @Mock
    private AlertHttpService alertHttpService;
    @Mock
    private Metrics metrics;

    private SimulationAwarePlannerService simulationAwarePlannerService;

    private ZonedDateTime now;

    @Before
    public void setUp() {
        final Clock clock = Clock.fixed(Instant.parse("2019-07-26T10:00:00Z"), ZoneOffset.UTC);
        now = ZonedDateTime.now(clock);

        simulationAwarePlannerService = new SimulationAwarePlannerService(
                PlannerProperties.builder()
                        .planEndpoint(PLAN_ENDPOINT)
                        .registerEndpoint(REGISTER_ENDPOINT)
                        .timeoutMs(100L)
                        .registerPeriodSeconds(60L)
                        .username(USERNAME)
                        .password(PASSWORD)
                        .build(),
                DeploymentProperties.builder().pbsHostId(PBS_HOST).pbsRegion(PBS_REGION).pbsVendor(PBS_VENDOR).build(),
                lineItemService,
                deliveryProgressService,
                alertHttpService,
                httpClient,
                metrics,
                clock,
                jacksonMapper);
    }

    @Test
    public void initiateLineItemFetchingShouldNotCallLineItemServiceUpdateMethod() throws JsonProcessingException {
        // given
        given(httpClient.get(anyString(), any(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, mapper.writeValueAsString(
                        Collections.singletonList(LineItemMetaData.builder().lineItemId("id").build())))));

        // when
        simulationAwarePlannerService.initiateLineItemsFetching(now);

        // then
        verify(lineItemService, never()).updateLineItems(any(), anyBoolean());
    }

    @Test
    public void initiateLineItemFetchingShouldNotRetryWhenCallToPlannerFailed() {
        // given
        given(httpClient.get(anyString(), any(), anyLong()))
                .willReturn(Future.failedFuture(new TimeoutException("time out")));

        // when
        simulationAwarePlannerService.initiateLineItemsFetching(now);

        // then
        verify(httpClient).get(anyString(), any(), anyLong());
    }

    @Test
    public void initiateLineItemFetchingShouldUpdateMetricsAndLineItemServiceWithResponsiveFlagWhenCallSuccessful()
            throws JsonProcessingException {
        // given
        given(httpClient.get(anyString(), any(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, mapper.writeValueAsString(
                        Collections.singletonList(LineItemMetaData.builder().lineItemId("id").build())))));

        // when
        simulationAwarePlannerService.initiateLineItemsFetching(now);

        // then
        @SuppressWarnings("unchecked")
        final List<LineItemMetaData> lineItemMetaData = (List<LineItemMetaData>) ReflectionTestUtils
                .getField(simulationAwarePlannerService, "lineItemMetaData");
        assertThat(lineItemMetaData).hasSize(1)
                .containsOnly(LineItemMetaData.builder().lineItemId("id").build());
        verify(metrics).updatePlannerRequestMetric(eq(true));
        verify(lineItemService).updateIsPlannerResponsive(eq(true));
    }

    @Test
    public void initiateLineItemFetchingShouldUpdateMetricsAndLineItemServiceWithResponsiveFlagWhenCallFailed() {
        // given
        given(httpClient.get(anyString(), any(), anyLong()))
                .willReturn(Future.failedFuture(new TimeoutException("time out")));

        // when
        simulationAwarePlannerService.initiateLineItemsFetching(now);

        // then
        verify(metrics).updatePlannerRequestMetric(eq(false));
        verify(lineItemService).updateIsPlannerResponsive(eq(false));
    }

    @Test
    public void advancePlansShouldCallUpdateLineItemsAndUpdateProgress() {
        // given and when
        simulationAwarePlannerService.advancePlans(now);

        // then
        verify(lineItemService).updateLineItems(anyList(), anyBoolean(), eq(now));
        verify(lineItemService).advanceToNextPlan(now);
    }
}
