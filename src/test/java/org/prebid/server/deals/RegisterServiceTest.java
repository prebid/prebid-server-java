package org.prebid.server.deals;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.deals.events.AdminEventService;
import org.prebid.server.deals.model.AdminCentralResponse;
import org.prebid.server.deals.model.AlertPriority;
import org.prebid.server.deals.model.DeploymentProperties;
import org.prebid.server.deals.model.PlannerProperties;
import org.prebid.server.deals.model.ServicesCommand;
import org.prebid.server.deals.proto.CurrencyServiceState;
import org.prebid.server.deals.proto.RegisterRequest;
import org.prebid.server.deals.proto.Status;
import org.prebid.server.deals.proto.report.DeliveryProgressReport;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.health.HealthMonitor;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class RegisterServiceTest extends VertxTest {

    private static final String PLAN_ENDPOINT = "plan-endpoint";
    private static final String REGISTER_ENDPOINT = "register-endpoint";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String PBS_HOST = "pbs-host";
    private static final String PBS_REGION = "pbs-region";
    private static final String PBS_VENDOR = "pbs-vendor";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private RegisterService registerService;
    @Mock
    private HealthMonitor healthMonitor;
    @Mock
    private CurrencyConversionService currencyConversionService;
    @Mock
    private AdminEventService adminEventService;
    @Mock
    private DeliveryProgressService deliveryProgressService;
    @Mock
    private HttpClient httpClient;
    @Mock
    private AlertHttpService alertHttpService;
    @Mock
    private Vertx vertx;

    private ZonedDateTime fixedDate;

    @Before
    public void setUp() {
        fixedDate = ZonedDateTime.now(Clock.fixed(Instant.parse("2019-07-26T10:00:00Z"), ZoneOffset.UTC));
        registerService = new RegisterService(
                PlannerProperties.builder()
                        .planEndpoint(PLAN_ENDPOINT)
                        .registerEndpoint(REGISTER_ENDPOINT)
                        .timeoutMs(100L)
                        .registerPeriodSeconds(60L)
                        .username(USERNAME)
                        .password(PASSWORD)
                        .build(),
                DeploymentProperties.builder().pbsHostId(PBS_HOST).pbsRegion(PBS_REGION).pbsVendor(PBS_VENDOR).build(),
                adminEventService,
                deliveryProgressService,
                alertHttpService,
                healthMonitor,
                currencyConversionService,
                httpClient,
                vertx,
                jacksonMapper);

        givenRegisterHttpResponse(200);
    }

    @Test
    public void initializeShouldSetRegisterTimer() throws JsonProcessingException {
        // given
        given(vertx.setPeriodic(anyLong(), any())).willReturn(1L);
        given(healthMonitor.calculateHealthIndex()).willReturn(BigDecimal.ONE);
        given(currencyConversionService.getLastUpdated()).willReturn(fixedDate);

        // when
        registerService.initialize();

        // then
        verify(vertx).setPeriodic(eq(60000L), any());
        verify(vertx, never()).cancelTimer(anyLong());

        final ArgumentCaptor<String> registerBodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(startsWith(REGISTER_ENDPOINT), any(), registerBodyCaptor.capture(), anyLong());
        assertThat(registerBodyCaptor.getValue()).isEqualTo(mapper.writeValueAsString(
                RegisterRequest.of(BigDecimal.ONE, Status.of(CurrencyServiceState.of("2019-07-26T10:00:00.000Z"), null),
                        PBS_HOST, PBS_REGION, PBS_VENDOR)));
    }

    @Test
    public void initializeShouldSetDeliveryReportToRegisterRequest() throws JsonProcessingException {
        // given
        given(vertx.setPeriodic(anyLong(), any())).willReturn(1L);
        given(healthMonitor.calculateHealthIndex()).willReturn(BigDecimal.ONE);
        given(deliveryProgressService.getOverallDeliveryProgressReport()).willReturn(DeliveryProgressReport.builder()
                .reportId("reportId").build());

        // when
        registerService.initialize();

        // then
        final ArgumentCaptor<String> registerBodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(httpClient).post(startsWith(REGISTER_ENDPOINT), any(), registerBodyCaptor.capture(), anyLong());
        assertThat(registerBodyCaptor.getValue()).isEqualTo(mapper.writeValueAsString(
                RegisterRequest.of(BigDecimal.ONE,
                        Status.of(null, DeliveryProgressReport.builder().reportId("reportId").build()),
                        PBS_HOST, PBS_REGION, PBS_VENDOR)));
    }

    @Test
    public void registerShouldNotCallAdminCentralWhenResponseIsEmpty() {
        // given
        given(healthMonitor.calculateHealthIndex()).willReturn(BigDecimal.ONE);
        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(), "")));

        // when
        registerService.register(MultiMap.caseInsensitiveMultiMap());

        // then
        verifyNoInteractions(adminEventService);
    }

    @Test
    public void registerShouldCallAdminCentralWhenResponseIsNotEmpty() throws JsonProcessingException {
        // given
        given(healthMonitor.calculateHealthIndex()).willReturn(BigDecimal.ONE);
        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(),
                        mapper.writeValueAsString(AdminCentralResponse.of(null, null, null, null, null,
                                ServicesCommand.of("stop"))))));

        // when
        registerService.register(MultiMap.caseInsensitiveMultiMap());

        // then
        verify(adminEventService).publishAdminCentralEvent(any());
    }

    @Test
    public void registerShouldNotCallAdminCentralWhenFutureFailed() {
        // given
        given(healthMonitor.calculateHealthIndex()).willReturn(BigDecimal.ONE);
        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.failedFuture("failed"));

        // when
        registerService.register(MultiMap.caseInsensitiveMultiMap());

        // then
        verifyNoInteractions(adminEventService);
    }

    @Test
    public void registerShouldCallAlertServiceWhenFutureFailed() {
        // given
        given(healthMonitor.calculateHealthIndex()).willReturn(BigDecimal.ONE);
        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.failedFuture("failed"));

        // when
        registerService.register(MultiMap.caseInsensitiveMultiMap());

        // then
        verify(alertHttpService).alertWithPeriod(eq("register"), eq("pbs-register-client-error"),
                eq(AlertPriority.MEDIUM),
                eq("Error occurred while registering with the Planner:"
                        + " io.vertx.core.impl.NoStackTraceThrowable: failed"));
    }

    @Test
    public void registerShouldCallAlertServiceResetWhenRequestWasSuccessful() {
        // given
        given(healthMonitor.calculateHealthIndex()).willReturn(BigDecimal.ONE);
        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, MultiMap.caseInsensitiveMultiMap(), "")));

        // when
        registerService.register(MultiMap.caseInsensitiveMultiMap());

        // then
        verify(alertHttpService).resetAlertCount(eq("pbs-register-client-error"));
    }

    @Test
    public void registerShouldNotSendAdminEventWhenResponseStatusIsBadRequest() {
        // given
        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(400, null, null)));

        // when
        registerService.register(MultiMap.caseInsensitiveMultiMap());

        // then
        verifyNoInteractions(adminEventService);
    }

    @Test
    public void registerShouldThrowPrebidExceptionWhenResponseIsInvalidJson() {
        // given
        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, "{")));

        // when and then
        assertThatThrownBy(() -> registerService.register(MultiMap.caseInsensitiveMultiMap()))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Cannot parse register response: {");
    }

    @Test
    public void suspendShouldStopRegisterTimer() {
        // when
        registerService.suspend();

        // then
        verify(vertx).cancelTimer(anyLong());
    }

    @Test
    public void initializeShouldCallHealthMonitor() {
        // when
        registerService.initialize();

        // then
        verify(healthMonitor).calculateHealthIndex();
    }

    private void givenRegisterHttpResponse(int statusCode) {
        final HttpClientResponse httpClientResponse = HttpClientResponse.of(statusCode, null, null);
        given(httpClient.post(startsWith(REGISTER_ENDPOINT), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));
    }
}
