package org.prebid.server.hooks.modules.intentiq.identity.metric;

import com.codahale.metrics.Counter;
import com.codahale.metrics.MetricRegistry;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MetricsPusherTest {

    private static final String URL = "https://push.example/api/v1/import/prometheus";
    private static final String PREFIX = "modules.module.intentiq-identity.";

    @Mock
    private Vertx vertx;

    @Mock
    private HttpClient httpClient;

    @Captor
    private ArgumentCaptor<String> bodyCaptor;

    @Captor
    private ArgumentCaptor<MultiMap> headersCaptor;

    @Captor
    private ArgumentCaptor<Handler<Long>> periodicCaptor;

    private MetricRegistry registry;

    private IntentiqIdentityMetrics metrics;

    private MetricsPusher target;

    @BeforeEach
    public void setUp() {
        registry = new MetricRegistry();
        metrics = new IntentiqIdentityMetrics(registry);
        target = new MetricsPusher(registry, metrics, vertx, httpClient, URL, "tok", "pbs-1", 60_000L, 5_000L);
    }

    @Test
    public void constructorShouldThrowWhenTokenIsNull() {
        assertThatNullPointerException().isThrownBy(() ->
                new MetricsPusher(registry, metrics, vertx, httpClient, URL, null, "pbs-1", 1L, 1L));
    }

    @Test
    public void startShouldScheduleThePushOnTheInterval() {
        // given
        metrics.apiSuccess("123");
        when(httpClient.post(anyString(), any(), anyString(), anyLong())).thenReturn(ok());

        // when
        target.start();

        // then
        verify(vertx).setPeriodic(eq(60_000L), periodicCaptor.capture());
        verifyNoInteractions(httpClient);
        periodicCaptor.getValue().handle(1L);
        verify(httpClient).post(eq(URL), any(), anyString(), eq(5_000L));
    }

    @Test
    public void renderShouldWriteCountersWithDpiAndInstanceLabels() {
        // given
        metrics.apiSuccess("125963644");
        metrics.apiSuccess("125963644");
        metrics.cacheHit("l1", "first_party", "125963644");
        metrics.terminationCause(41, "125963644");
        metrics.l2GetError();

        // when
        final String body = target.render();

        // then
        assertThat(body).contains(
                "iiq_pbs_api_success_total{instance=\"pbs-1\",dpi=\"125963644\"} 2\n",
                "iiq_pbs_cache_l1_hit_first_party_total{instance=\"pbs-1\",dpi=\"125963644\"} 1\n",
                "iiq_pbs_tc_41_total{instance=\"pbs-1\",dpi=\"125963644\"} 1\n",
                "iiq_pbs_l2_get_error_total{instance=\"pbs-1\"} 1\n");
    }

    @Test
    public void renderShouldWriteTimerCountAndQuantilesInMilliseconds() {
        // given
        metrics.apiLatency(TimeUnit.MILLISECONDS.toNanos(12), "123");

        // when
        final String body = target.render();

        // then
        assertThat(body).contains(
                "iiq_pbs_api_latency_count{instance=\"pbs-1\",dpi=\"123\"} 1\n",
                "iiq_pbs_api_latency_ms{instance=\"pbs-1\",dpi=\"123\",quantile=\"0.5\"} 12.0\n",
                "iiq_pbs_api_latency_ms{instance=\"pbs-1\",dpi=\"123\",quantile=\"0.99\"} 12.0\n");
    }

    @Test
    public void renderShouldWriteGauges() {
        // given
        metrics.registerL1Gauges(() -> 42L, () -> 3L);

        // when
        final String body = target.render();

        // then
        assertThat(body).contains(
                "iiq_pbs_l1_size{instance=\"pbs-1\"} 42\n",
                "iiq_pbs_l1_eviction{instance=\"pbs-1\"} 3\n");
    }

    @Test
    public void renderShouldShortenTheFrameworkHookMetricNames() {
        // given
        registry.counter(PREFIX + "stage.procauction.hook.intentiq-identity-processed-auction-request-hook.timeout")
                .inc();

        // when
        final String body = target.render();

        // then
        assertThat(body).contains("iiq_pbs_stage_procauction_hook_enrich_timeout_total{instance=\"pbs-1\"} 1\n");
    }

    @Test
    public void renderShouldSkipCountersThatResetOnReadAndLeaveThemUntouched() {
        // given
        final Counter resetting = new Counter() {
            @Override
            public long getCount() {
                final long count = super.getCount();
                dec(count);
                return count;
            }
        };
        registry.register(PREFIX + "stage.procauction.hook.x.call", resetting);
        resetting.inc(5);

        // when
        final String body = target.render();

        // then
        assertThat(body).doesNotContain("hook_x_call");
        assertThat(resetting.getCount()).isEqualTo(5);
    }

    @Test
    public void renderShouldIgnoreMetricsOfOtherModulesAndTheCore() {
        // given
        registry.counter("requests.ok.openrtb2-web").inc();
        registry.counter("modules.module.other.custom.x").inc();

        // when and then
        assertThat(target.render()).isEmpty();
    }

    @Test
    public void pushShouldSendABearerTokenAndThePrometheusContentType() {
        // given
        metrics.apiSuccess("123");
        when(httpClient.post(anyString(), any(), anyString(), anyLong())).thenReturn(ok());

        // when
        target.push();

        // then
        verify(httpClient).post(eq(URL), headersCaptor.capture(), bodyCaptor.capture(), eq(5_000L));
        assertThat(headersCaptor.getValue().get("Authorization")).isEqualTo("Bearer tok");
        assertThat(headersCaptor.getValue().get("Content-Type")).startsWith("text/plain");
        assertThat(bodyCaptor.getValue()).contains("iiq_pbs_api_success_total");
        assertThat(registry.counter(PREFIX + "custom.push.success").getCount()).isEqualTo(1);
    }

    @Test
    public void pushShouldSendNothingWhenThereIsNothingToPush() {
        // when
        target.push();

        // then
        verifyNoInteractions(httpClient);
    }

    @Test
    public void pushShouldSkipTheTickWhileThePreviousPushIsStillRunning() {
        // given
        metrics.apiSuccess("123");
        final Promise<HttpClientResponse> pending = Promise.promise();
        when(httpClient.post(anyString(), any(), anyString(), anyLong())).thenReturn(pending.future());

        // when
        target.push();
        target.push();

        // then
        verify(httpClient, times(1)).post(anyString(), any(), anyString(), anyLong());
        assertThat(registry.counter(PREFIX + "custom.push.skipped").getCount()).isEqualTo(1);

        // and when the first push ends, the next tick sends again
        pending.complete(HttpClientResponse.of(204, null, null));
        target.push();
        verify(httpClient, times(2)).post(anyString(), any(), anyString(), anyLong());
    }

    @Test
    public void pushShouldCountAFailureAndDropTheBatch() {
        // given
        metrics.apiSuccess("123");
        when(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .thenReturn(Future.failedFuture(new RuntimeException("timeout")));

        // when
        target.push();
        target.push();

        // then: each tick sends once, nothing is retried or queued
        verify(httpClient, times(2)).post(anyString(), any(), anyString(), anyLong());
        assertThat(registry.counter(PREFIX + "custom.push.error").getCount()).isEqualTo(2);
    }

    @Test
    public void pushShouldCountANon2xxAnswerAsAnError() {
        // given
        metrics.apiSuccess("123");
        when(httpClient.post(anyString(), any(), anyString(), anyLong()))
                .thenReturn(Future.succeededFuture(HttpClientResponse.of(401, null, null)));

        // when
        target.push();

        // then
        assertThat(registry.counter(PREFIX + "custom.push.error").getCount()).isEqualTo(1);
        assertThat(registry.counter(PREFIX + "custom.push.success").getCount()).isZero();
    }

    @Test
    public void resolveInstanceShouldPreferTheConfiguredValue() {
        assertThat(MetricsPusher.resolveInstance("pbs-eu-7")).isEqualTo("pbs-eu-7");
        assertThat(MetricsPusher.resolveInstance(null)).isNotBlank();
    }

    private static Future<HttpClientResponse> ok() {
        return Future.succeededFuture(HttpClientResponse.of(204, null, null));
    }
}
