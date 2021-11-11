package org.prebid.server.deals;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Vertx;
import org.apache.http.HttpHeaders;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.deals.lineitem.DeliveryProgress;
import org.prebid.server.deals.model.AlertPriority;
import org.prebid.server.deals.model.DeliveryStatsProperties;
import org.prebid.server.deals.proto.report.DeliveryProgressReport;
import org.prebid.server.deals.proto.report.DeliveryProgressReportBatch;
import org.prebid.server.deals.proto.report.LineItemStatus;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.NavigableSet;
import java.util.concurrent.TimeoutException;
import java.util.zip.GZIPInputStream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class DeliveryStatsServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private DeliveryProgressReportFactory deliveryProgressReportFactory;
    @Mock
    private AlertHttpService alertHttpService;
    @Mock
    private HttpClient httpClient;
    @Mock
    private Clock clock;
    @Mock
    private Metrics metrics;

    @Mock
    private Vertx vertx;

    private DeliveryStatsService deliveryStatsService;

    private ZonedDateTime now;

    @Mock
    private LineItemService lineItemService;

    @Before
    public void setUp() {
        now = ZonedDateTime.now(Clock.fixed(Instant.parse("2019-07-26T10:00:00Z"), ZoneOffset.UTC));
        given(clock.instant()).willReturn(now.toInstant());
        given(clock.getZone()).willReturn(ZoneOffset.UTC);

        deliveryStatsService = new DeliveryStatsService(
                DeliveryStatsProperties.builder()
                        .endpoint("localhost/delivery")
                        .cachedReportsNumber(3)
                        .timeoutMs(500L)
                        .reportsIntervalMs(0)
                        .batchesIntervalMs(0)
                        .username("username")
                        .password("password")
                        .build(),
                deliveryProgressReportFactory,
                alertHttpService,
                httpClient,
                metrics,
                clock,
                vertx,
                jacksonMapper);
    }

    @SuppressWarnings("unchecked")
    @Test
    public void sendDeliveryProgressReportShouldSendBothBatches() {
        // given
        givenDeliveryProgressHttpResponse(httpClient, 200, null);

        given(deliveryProgressReportFactory.batchFromDeliveryProgress(any(), any(), any(), anyInt(), anyBoolean()))
                .willReturn(
                        DeliveryProgressReportBatch.of(singleton(DeliveryProgressReport.builder().reportId("1")
                                        .lineItemStatus(emptySet())
                                        .dataWindowEndTimeStamp(now.minusHours(2).toString()).build()), "1",
                                now.minusHours(2).toString()),
                        DeliveryProgressReportBatch.of(singleton(DeliveryProgressReport.builder().reportId("2")
                                        .lineItemStatus(emptySet())
                                        .dataWindowEndTimeStamp(now.minusHours(1).toString()).build()), "2",
                                now.minusHours(1).toString()));

        final DeliveryProgress deliveryProgress1 = DeliveryProgress.of(now.minusHours(3), lineItemService);
        final DeliveryProgress deliveryProgress2 = DeliveryProgress.of(now.minusHours(2), lineItemService);

        // when
        deliveryStatsService.addDeliveryProgress(deliveryProgress1, emptyMap());
        deliveryStatsService.addDeliveryProgress(deliveryProgress2, emptyMap());
        deliveryStatsService.sendDeliveryProgressReports();

        // then
        verify(httpClient, times(2)).post(anyString(), any(), anyString(), anyLong());
        final NavigableSet<DeliveryProgress> reports = (NavigableSet<DeliveryProgress>)
                ReflectionTestUtils.getField(deliveryStatsService, "requiredBatches");
        assertThat(reports).isEmpty();
        verify(metrics, times(2)).updateDeliveryRequestMetric(eq(true));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void sendDeliveryProgressReportShouldSendOneBatchAndCacheFailedBatch() {
        // given
        final DeliveryProgress deliveryProgress1 = DeliveryProgress.of(now.minusHours(3), lineItemService);
        final DeliveryProgress deliveryProgress2 = DeliveryProgress.of(now.minusHours(2), lineItemService);

        given(deliveryProgressReportFactory.batchFromDeliveryProgress(any(), any(), any(), anyInt(), anyBoolean()))
                .willReturn(
                        DeliveryProgressReportBatch.of(singleton(DeliveryProgressReport.builder().reportId("1")
                                        .lineItemStatus(emptySet())
                                        .dataWindowEndTimeStamp(now.minusHours(2).toString()).build()), "1",
                                now.minusHours(2).toString()),
                        DeliveryProgressReportBatch.of(singleton(DeliveryProgressReport.builder().reportId("2")
                                        .lineItemStatus(emptySet())
                                        .dataWindowEndTimeStamp(now.minusHours(1).toString()).build()), "2",
                                now.minusHours(1).toString()));

        deliveryStatsService.addDeliveryProgress(deliveryProgress1, null);
        deliveryStatsService.addDeliveryProgress(deliveryProgress2, null);

        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, null)),
                        Future.failedFuture(new TimeoutException()));

        deliveryStatsService.sendDeliveryProgressReports();

        // when and then
        verify(httpClient, times(2)).post(anyString(), any(), anyString(), anyLong());
        final NavigableSet<DeliveryProgress> reports = (NavigableSet<DeliveryProgress>)
                ReflectionTestUtils.getField(deliveryStatsService, "requiredBatches");
        assertThat(reports).hasSize(1);
        verify(metrics).updateDeliveryRequestMetric(eq(true));
        verify(metrics).updateDeliveryRequestMetric(eq(false));
    }

    @Test
    public void sendDeliveryProgressReportShouldSendFirstReportFromFirstBatchFailOnSecondsAndCacheOther() {
        // given
        final DeliveryProgress deliveryProgress1 = DeliveryProgress.of(now.minusHours(3), lineItemService);
        final DeliveryProgress deliveryProgress2 = DeliveryProgress.of(now.minusHours(2), lineItemService);

        given(deliveryProgressReportFactory.batchFromDeliveryProgress(any(), any(), any(), anyInt(), anyBoolean()))
                .willReturn(
                        DeliveryProgressReportBatch.of(
                                new HashSet<>(asList(DeliveryProgressReport.builder().reportId("1")
                                                .lineItemStatus(singleton(LineItemStatus.builder().lineItemId("1")
                                                        .build()))
                                                .dataWindowEndTimeStamp(now.minusHours(2).toString()).build(),
                                        DeliveryProgressReport.builder().reportId("1")
                                                .lineItemStatus(singleton(LineItemStatus.builder().lineItemId("2")
                                                        .build()))
                                                .dataWindowEndTimeStamp(now.minusHours(2).toString()).build())),
                                "1", now.minusHours(2).toString()),
                        DeliveryProgressReportBatch.of(
                                new HashSet<>(asList(DeliveryProgressReport.builder().reportId("2")
                                                .lineItemStatus(singleton(LineItemStatus.builder().lineItemId("1")
                                                        .build()))
                                                .dataWindowEndTimeStamp(now.minusHours(3).toString()).build(),
                                        DeliveryProgressReport.builder().reportId("2")
                                                .lineItemStatus(singleton(LineItemStatus.builder().lineItemId("2")
                                                        .build()))
                                                .dataWindowEndTimeStamp(now.minusHours(3).toString()).build())),
                                "2", now.minusHours(3).toString()));

        deliveryStatsService.addDeliveryProgress(deliveryProgress1, null);
        deliveryStatsService.addDeliveryProgress(deliveryProgress2, null);

        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(200, null, null)),
                        Future.failedFuture(new TimeoutException()));

        deliveryStatsService.sendDeliveryProgressReports();

        // when and then
        verify(httpClient, times(2)).post(anyString(), any(), anyString(), anyLong());
        final NavigableSet<DeliveryProgressReportBatch> reports = (NavigableSet<DeliveryProgressReportBatch>)
                ReflectionTestUtils.getField(deliveryStatsService, "requiredBatches");
        assertThat(reports).hasSize(2)
                .flatExtracting(DeliveryProgressReportBatch::getReports)
                .hasSize(3);
        verify(metrics).updateDeliveryRequestMetric(eq(true));
        verify(metrics).updateDeliveryRequestMetric(eq(false));
    }

    @Test
    public void sendDeliveryProgressReportShouldHandleFailedBatchesCacheLimitWhenResponseStatusIsBadRequest() {
        // given
        given(deliveryProgressReportFactory.batchFromDeliveryProgress(any(), any(), any(), anyInt(), anyBoolean()))
                .willReturn(
                        DeliveryProgressReportBatch.of(singleton(DeliveryProgressReport.builder().reportId("1")
                                        .dataWindowEndTimeStamp(now.minusHours(4).toString()).build()), "1",
                                now.minusHours(4).toString()),
                        DeliveryProgressReportBatch.of(singleton(DeliveryProgressReport.builder().reportId("2")
                                        .dataWindowEndTimeStamp(now.minusHours(3).toString()).build()), "2",
                                now.minusHours(3).toString()),
                        DeliveryProgressReportBatch.of(singleton(DeliveryProgressReport.builder().reportId("3")
                                        .dataWindowEndTimeStamp(now.minusHours(2).toString()).build()), "3",
                                now.minusHours(2).toString()),
                        DeliveryProgressReportBatch.of(singleton(DeliveryProgressReport.builder().reportId("4")
                                        .dataWindowEndTimeStamp(now.minusHours(1).toString()).build()), "4",
                                now.minusHours(1).toString()));

        final DeliveryProgress deliveryProgress1 = DeliveryProgress.of(now.minusHours(5), lineItemService);
        final DeliveryProgress deliveryProgress2 = DeliveryProgress.of(now.minusHours(4), lineItemService);
        final DeliveryProgress deliveryProgress3 = DeliveryProgress.of(now.minusHours(3), lineItemService);
        final DeliveryProgress deliveryProgress4 = DeliveryProgress.of(now.minusHours(2), lineItemService);

        deliveryStatsService.addDeliveryProgress(deliveryProgress1, null);
        deliveryStatsService.addDeliveryProgress(deliveryProgress2, null);
        deliveryStatsService.addDeliveryProgress(deliveryProgress3, null);
        deliveryStatsService.addDeliveryProgress(deliveryProgress4, null);

        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(HttpClientResponse.of(400, null, null)));

        deliveryStatsService.sendDeliveryProgressReports();

        // when and then
        verify(httpClient).post(anyString(), any(), anyString(), anyLong());
        @SuppressWarnings("unchecked") final NavigableSet<DeliveryProgress> reports = (NavigableSet<DeliveryProgress>)
                ReflectionTestUtils.getField(deliveryStatsService, "requiredBatches");
        assertThat(reports).hasSize(3);
    }

    @Test
    public void sendDeliveryProgressReportShouldShouldRemoveReportFromQueueWhenDelStatsRespondWith409Conflict() {
        // given
        givenDeliveryProgressHttpResponse(httpClient, 409, null);

        given(deliveryProgressReportFactory.batchFromDeliveryProgress(any(), any(), any(), anyInt(), anyBoolean()))
                .willReturn(DeliveryProgressReportBatch.of(singleton(DeliveryProgressReport.builder().reportId("1")
                                .lineItemStatus(emptySet())
                                .dataWindowEndTimeStamp(now.minusHours(2).toString()).build()), "1",
                        now.minusHours(2).toString()));

        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now.minusHours(3), lineItemService);

        // when
        deliveryStatsService.addDeliveryProgress(deliveryProgress, emptyMap());
        deliveryStatsService.sendDeliveryProgressReports();

        // then
        final NavigableSet<DeliveryProgress> reports =
                (NavigableSet<DeliveryProgress>) ReflectionTestUtils.getField(deliveryStatsService, "requiredBatches");
        assertThat(reports).isEmpty();
    }

    @Test
    public void sendDeliveryProgressReportShouldCallAlertServiceWhenRequestFailed() {
        // given
        given(deliveryProgressReportFactory.batchFromDeliveryProgress(any(), any(), any(), anyInt(), anyBoolean()))
                .willReturn(DeliveryProgressReportBatch.of(singleton(DeliveryProgressReport.builder().reportId("1")
                                .dataWindowEndTimeStamp(now.minusHours(2).toString()).build()), "1",
                        now.minusHours(2).toString()));

        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now.minusHours(3), lineItemService);

        deliveryStatsService.addDeliveryProgress(deliveryProgress, emptyMap());

        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.failedFuture(new TimeoutException("Timeout")));

        // when
        deliveryStatsService.sendDeliveryProgressReports();

        // then
        verify(alertHttpService).alertWithPeriod(eq("deliveryStats"), eq("pbs-delivery-stats-client-error"),
                eq(AlertPriority.MEDIUM),
                eq("Report was not send to delivery stats service with a reason: Sending report with id = 1 failed"
                        + " in a reason: Timeout"));
    }

    @Test
    public void sendDeliveryProgressReportShouldCallAlertServiceResetWhenRequestWasSuccessful() {
        // given
        givenDeliveryProgressHttpResponse(httpClient, 200, null);

        given(deliveryProgressReportFactory.batchFromDeliveryProgress(any(), any(), any(), anyInt(), anyBoolean()))
                .willReturn(DeliveryProgressReportBatch.of(singleton(DeliveryProgressReport.builder().reportId("1")
                                .lineItemStatus(emptySet())
                                .dataWindowEndTimeStamp(now.minusHours(2).toString()).build()), "1",
                        now.minusHours(2).toString()));

        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now.minusHours(3), lineItemService);

        // when
        deliveryStatsService.addDeliveryProgress(deliveryProgress, emptyMap());
        deliveryStatsService.sendDeliveryProgressReports();

        // then
        verify(alertHttpService).resetAlertCount(eq("pbs-delivery-stats-client-error"));
    }

    @Test
    public void suspendShouldSetSuspendFlagAndReportShouldNotBeSent() {
        // given
        given(deliveryProgressReportFactory.batchFromDeliveryProgress(any(), any(), any(), anyInt(), anyBoolean()))
                .willReturn(DeliveryProgressReportBatch.of(singleton(DeliveryProgressReport.builder().reportId("1")
                                .dataWindowEndTimeStamp(now.minusHours(4).toString()).build()), "1",
                        now.minusHours(4).toString()));

        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now.minusHours(5), lineItemService);

        deliveryStatsService.addDeliveryProgress(deliveryProgress, null);

        // when
        deliveryStatsService.suspend();
        deliveryStatsService.sendDeliveryProgressReports();

        // then
        verifyNoInteractions(httpClient);
    }

    @Test
    public void sendDeliveryProgressReportShouldSendGzippedBody() throws JsonProcessingException {
        // given
        final DeliveryStatsService deliveryStatsService = new DeliveryStatsService(
                DeliveryStatsProperties.builder()
                        .endpoint("localhost/delivery")
                        .cachedReportsNumber(3)
                        .timeoutMs(500L)
                        .reportsIntervalMs(0)
                        .requestCompressionEnabled(true)
                        .username("username")
                        .password("password")
                        .build(),
                deliveryProgressReportFactory,
                alertHttpService,
                httpClient,
                metrics,
                clock,
                vertx,
                jacksonMapper);

        givenDeliveryProgressHttpResponse(httpClient, 200, null);

        final DeliveryProgressReport deliveryProgressReport = DeliveryProgressReport.builder().reportId("1")
                .lineItemStatus(emptySet())
                .dataWindowEndTimeStamp(now.minusHours(2).toString()).build();
        given(deliveryProgressReportFactory.updateReportTimeStamp(any(), any())).willReturn(deliveryProgressReport);
        given(deliveryProgressReportFactory.batchFromDeliveryProgress(any(), any(), any(), anyInt(), anyBoolean()))
                .willReturn(
                        DeliveryProgressReportBatch.of(singleton(deliveryProgressReport), "1",
                                now.minusHours(2).toString()));

        final DeliveryProgress deliveryProgress = DeliveryProgress.of(now.minusHours(3), lineItemService);

        // when
        deliveryStatsService.addDeliveryProgress(deliveryProgress, emptyMap());
        deliveryStatsService.sendDeliveryProgressReports();

        // then
        final ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
        final ArgumentCaptor<MultiMap> headerCaptor = ArgumentCaptor.forClass(MultiMap.class);
        verify(httpClient).request(any(), anyString(), headerCaptor.capture(), bodyCaptor.capture(), anyLong());
        // verify body was compressed well
        final byte[] compressedRequestBody = bodyCaptor.getValue();
        final String decompressedRequestBody = decompress(compressedRequestBody);
        assertThat(mapper.readValue(decompressedRequestBody, DeliveryProgressReport.class))
                .isEqualTo(deliveryProgressReport);
        // verify Content-encoding header was added
        final MultiMap headers = headerCaptor.getValue();
        assertThat(headers.get(HttpHeaders.CONTENT_ENCODING)).isEqualTo("gzip");
    }

    private static String decompress(byte[] byteArray) {
        final StringBuilder body = new StringBuilder();
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(byteArray));
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(gzipInputStream,
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                body.append(line);
            }
            return body.toString();
        } catch (IOException e) {
            throw new RuntimeException("Error occurred while decompressing gzipped body");
        }
    }

    private static void givenDeliveryProgressHttpResponse(HttpClient httpClient, int statusCode, String response) {
        final HttpClientResponse httpClientResponse = HttpClientResponse.of(statusCode, null, response);
        given(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));
    }
}
