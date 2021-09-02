package org.prebid.server.deals;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.deals.model.AlertPriority;
import org.prebid.server.deals.model.DeploymentProperties;
import org.prebid.server.deals.model.PlannerProperties;
import org.prebid.server.deals.proto.DeliverySchedule;
import org.prebid.server.deals.proto.FrequencyCap;
import org.prebid.server.deals.proto.LineItemMetaData;
import org.prebid.server.deals.proto.Price;
import org.prebid.server.deals.proto.Token;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PlannerServiceTest extends VertxTest {

    private static final String PLAN_ENDPOINT = "plan-endpoint";
    private static final String REGISTER_ENDPOINT = "register-endpoint";
    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String PBS_HOST = "pbs-host";
    private static final String PBS_REGION = "pbs-region";
    private static final String PBS_VENDOR = "pbs-vendor";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;
    @Mock
    private LineItemService lineItemService;
    @Mock
    private DeliveryProgressService deliveryProgressService;
    @Mock
    private AlertHttpService alertHttpService;
    @Mock
    private Metrics metrics;
    @Mock
    private Clock clock;

    private PlannerService plannerService;

    private ZonedDateTime now;

    @Before
    public void setUp() throws JsonProcessingException {
        clock = Clock.fixed(Instant.parse("2019-07-26T10:00:00Z"), ZoneOffset.UTC);
        now = ZonedDateTime.now(clock);

        plannerService = new PlannerService(
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

        givenPlanHttpResponse(200, mapper.writeValueAsString(
                asList(givenLineItemMetaData("lineItem1", "1001", "rubicon", now),
                        givenLineItemMetaData("lineItem2", "1002", "appnexus", now))));
    }

    @Test
    public void updateLineItemMetaDataShouldRetryOnceWhenResponseCantBeParsed() {
        // given
        givenPlanHttpResponse(200, "{");

        // when
        plannerService.updateLineItemMetaData();

        // then
        verify(lineItemService, never()).updateLineItems(any(), anyBoolean());
        verify(alertHttpService).alertWithPeriod(eq("planner"),
                eq("pbs-planner-client-error"),
                eq(AlertPriority.MEDIUM),
                eq("Failed to retrieve line items from GP. Reason: Cannot parse response: {"));
        verify(metrics, times(2)).updatePlannerRequestMetric(eq(false));
        verify(httpClient, times(2)).get(anyString(), any(), anyLong());
    }

    @Test
    public void updateLineItemMetaDataShouldRetryWhenHttpClientReturnsFailedFuture() {
        // given
        given(httpClient.get(anyString(), any(), anyLong()))
                .willReturn(Future.failedFuture(new TimeoutException("Timeout has been exceeded")));

        // when
        plannerService.updateLineItemMetaData();

        // then
        verify(alertHttpService).alertWithPeriod(eq("planner"), eq("pbs-planner-client-error"),
                eq(AlertPriority.MEDIUM),
                eq("Failed to retrieve line items from GP. Reason: Timeout has been exceeded"));
        verify(httpClient, times(2)).get(anyString(), any(), anyLong());
    }

    @Test
    public void updateLineItemMetaDataShouldAlertWithoutRetryWhenPlannerReturnsEmptyLineItemList()
            throws JsonProcessingException {
        // given
        givenPlanHttpResponse(200, mapper.writeValueAsString(emptyList()));

        // when
        plannerService.updateLineItemMetaData();

        // then
        verify(alertHttpService).alertWithPeriod(eq("planner"), eq("pbs-planner-empty-response-error"),
                eq(AlertPriority.LOW), eq("Response without line items was received from planner"));
        verify(httpClient).get(anyString(), any(), anyLong());
        verify(lineItemService).updateLineItems(eq(emptyList()), anyBoolean());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getIdToLineItemsShouldReturnLineItemsSuccessfulInitialization() {
        // when
        plannerService.updateLineItemMetaData();

        // then
        final ArgumentCaptor<List<LineItemMetaData>> planResponseCaptor = ArgumentCaptor.forClass(List.class);
        verify(lineItemService).updateLineItems(planResponseCaptor.capture(), anyBoolean());
        assertThat(planResponseCaptor.getValue()).isEqualTo(asList(
                givenLineItemMetaData("lineItem1", "1001", "rubicon", now),
                givenLineItemMetaData("lineItem2", "1002", "appnexus", now)));
        verify(alertHttpService).resetAlertCount(eq("pbs-planner-empty-response-error"));
        verify(metrics).updatePlannerRequestMetric(eq(true));
        verify(metrics).updateLineItemsNumberMetric(eq(2L));
        verify(metrics).updateRequestTimeMetric(eq(MetricName.planner_request_time), anyLong());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getIdToLineItemsShouldReturnOverwrittenMetaDataAfterSchedulerCallsRefresh()
            throws JsonProcessingException {
        // given
        givenHttpClientReturnsResponses(
                Future.succeededFuture(HttpClientResponse.of(200, null,
                        mapper.writeValueAsString(singletonList(LineItemMetaData.builder().lineItemId("id1")
                                .deliverySchedules(singletonList(DeliverySchedule.builder()
                                        .planId("id1")
                                        .startTimeStamp(now.minusHours(1))
                                        .endTimeStamp(now.plusHours(1))
                                        .updatedTimeStamp(now)
                                        .tokens(singleton(Token.of(1, 300))).build()))
                                .accountId("1").build())))),
                Future.succeededFuture(HttpClientResponse.of(200, null,
                        mapper.writeValueAsString(singletonList(LineItemMetaData.builder().lineItemId("id2")
                                .deliverySchedules(singletonList(DeliverySchedule.builder()
                                        .planId("id2")
                                        .startTimeStamp(now.minusHours(1))
                                        .endTimeStamp(now.plusHours(1))
                                        .updatedTimeStamp(now)
                                        .tokens(singleton(Token.of(1, 300))).build()))
                                .accountId("2").build())))));

        // when and then
        plannerService.updateLineItemMetaData();

        // fire request seconds time
        plannerService.updateLineItemMetaData();

        verify(httpClient, times(2)).get(anyString(), any(), anyLong());
        verify(lineItemService, times(2)).updateLineItems(any(), anyBoolean());

        final ArgumentCaptor<List<LineItemMetaData>> planResponseCaptor = ArgumentCaptor.forClass(List.class);
        verify(lineItemService, times(2)).updateLineItems(planResponseCaptor.capture(), anyBoolean());

        assertThat(planResponseCaptor.getAllValues().get(1))
                .isEqualTo(singletonList(LineItemMetaData.builder().lineItemId("id2")
                        .deliverySchedules(singletonList(DeliverySchedule.builder()
                                .planId("id2")
                                .startTimeStamp(now.minusHours(1))
                                .endTimeStamp(now.plusHours(1))
                                .updatedTimeStamp(now)
                                .tokens(singleton(Token.of(1, 300))).build()))
                        .accountId("2").build()));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getIdToLineItemMetaDataShouldReturnExpectedResult() {
        // when
        plannerService.updateLineItemMetaData();

        // then
        final ArgumentCaptor<List<LineItemMetaData>> planResponseCaptor = ArgumentCaptor.forClass(List.class);
        verify(lineItemService).updateLineItems(planResponseCaptor.capture(), anyBoolean());

        assertThat(planResponseCaptor.getValue())
                .containsOnly(
                        givenLineItemMetaData("lineItem1", "1001", "rubicon", now),
                        givenLineItemMetaData("lineItem2", "1002", "appnexus", now));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getIdToLineItemMetaDataShouldNotCallUpdateLineItemAfterFailedRequest() {
        // given
        givenPlanHttpResponse(404, null);

        // when
        plannerService.updateLineItemMetaData();

        // then
        final ArgumentCaptor<List<LineItemMetaData>> planResponseCaptor = ArgumentCaptor.forClass(List.class);
        verify(lineItemService, never()).updateLineItems(planResponseCaptor.capture(), anyBoolean());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getIdToLineItemMetaDataShouldNotCallUpdateLineItemsWhenBodyIsNull() {
        // given
        givenPlanHttpResponse(200, null);

        // when
        plannerService.updateLineItemMetaData();

        // then
        final ArgumentCaptor<List<LineItemMetaData>> planResponseCaptor = ArgumentCaptor.forClass(List.class);
        verify(lineItemService, never()).updateLineItems(planResponseCaptor.capture(), anyBoolean());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getIdToLineItemMetaDataShouldNotCallUpdateLineItemWhenBodyCantBeParsed() {
        // given
        givenPlanHttpResponse(200, "{");

        // when
        plannerService.updateLineItemMetaData();

        // then
        final ArgumentCaptor<List<LineItemMetaData>> planResponseCaptor = ArgumentCaptor.forClass(List.class);
        verify(lineItemService, never()).updateLineItems(planResponseCaptor.capture(), anyBoolean());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getIdToLineItemsShouldNotCallLineItemsUpdateAfter404Response() {
        // given
        givenPlanHttpResponse(404, null);

        // when
        plannerService.updateLineItemMetaData();

        // then
        final ArgumentCaptor<List<LineItemMetaData>> planResponseCaptor = ArgumentCaptor.forClass(List.class);
        verify(lineItemService, never()).updateLineItems(planResponseCaptor.capture(), anyBoolean());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void getIdToLineItemsShouldNotCallLineItemsUpdateWhenBodyIsNull() {
        // given
        givenPlanHttpResponse(200, null);

        // when
        plannerService.updateLineItemMetaData();

        // then
        final ArgumentCaptor<List<LineItemMetaData>> planResponseCaptor = ArgumentCaptor.forClass(List.class);
        verify(lineItemService, never()).updateLineItems(planResponseCaptor.capture(), anyBoolean());
    }

    private void givenPlanHttpResponse(int statusCode, String response) {
        final HttpClientResponse httpClientResponse = HttpClientResponse.of(statusCode, null, response);
        given(httpClient.get(startsWith(PLAN_ENDPOINT), any(), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));
    }

    private static LineItemMetaData givenLineItemMetaData(String lineItemId, String account, String bidderCode,
                                                          ZonedDateTime now) {
        return LineItemMetaData.builder()
                .lineItemId(lineItemId)
                .dealId("dealId")
                .accountId(account)
                .source(bidderCode)
                .price(Price.of(BigDecimal.ONE, "USD"))
                .relativePriority(5)
                .startTimeStamp(now)
                .endTimeStamp(now)
                .updatedTimeStamp(now)
                .frequencyCaps(singletonList(FrequencyCap.builder()
                        .fcapId("fcap").count(6L).periods(7).periodType("day").build()))
                .deliverySchedules(singletonList(DeliverySchedule.builder()
                        .planId("plan")
                        .startTimeStamp(now.minusHours(1))
                        .endTimeStamp(now.plusHours(1))
                        .updatedTimeStamp(now)
                        .tokens(singleton(Token.of(1, 300))).build()))
                .targeting(mapper.createObjectNode())
                .build();
    }

    @SafeVarargs
    private final void givenHttpClientReturnsResponses(Future<HttpClientResponse>... futureHttpClientResponses) {
        BDDMockito.BDDMyOngoingStubbing<Future<HttpClientResponse>> stubbing =
                given(httpClient.get(anyString(), any(), anyLong()));

        // setup multiple answers
        for (Future<HttpClientResponse> futureHttpClientResponse : futureHttpClientResponses) {
            stubbing = stubbing.willReturn(futureHttpClientResponse);
        }
    }
}
