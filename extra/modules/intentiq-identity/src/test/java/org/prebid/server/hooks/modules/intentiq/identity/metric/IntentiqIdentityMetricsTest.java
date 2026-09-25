package org.prebid.server.hooks.modules.intentiq.identity.metric;

import com.codahale.metrics.MetricRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntentiqIdentityMetricsTest {

    private static final String DPI = "15769";

    private MetricRegistry metricRegistry;
    private IntentiqIdentityMetrics target;

    @BeforeEach
    public void setUp() {
        metricRegistry = new MetricRegistry();
        target = new IntentiqIdentityMetrics(metricRegistry);
    }

    @Test
    public void shouldIncrementNamespacedCountersSuffixedWithDpi() {
        // when
        target.cacheHit("l1", "third_party", DPI);
        target.cacheHit("l2", "third_party", DPI);
        target.cacheNegativeHit("l2", "device", DPI);
        target.cacheInProgress("l1", "device", DPI);
        target.cacheMiss("third_party", DPI);
        target.cacheMiss("device", DPI);
        target.apiSuccess(DPI);
        target.apiError(DPI);
        target.enriched(DPI);
        target.eidsNone(DPI);
        target.skipNoEndpoint(DPI);
        target.impressionReported(DPI);
        target.impressionError(DPI);

        // then
        assertThat(count("cache.l1.hit.third_party_" + DPI)).isEqualTo(1);
        assertThat(count("cache.l2.hit.third_party_" + DPI)).isEqualTo(1);
        assertThat(count("cache.l2.negative.hit.device_" + DPI)).isEqualTo(1);
        assertThat(count("cache.l1.in_progress.device_" + DPI)).isEqualTo(1);
        assertThat(count("cache.miss.third_party_" + DPI)).isEqualTo(1);
        assertThat(count("cache.miss.device_" + DPI)).isEqualTo(1);
        assertThat(count("api.success_" + DPI)).isEqualTo(1);
        assertThat(count("api.error_" + DPI)).isEqualTo(1);
        assertThat(count("enriched_" + DPI)).isEqualTo(1);
        assertThat(count("eids.none_" + DPI)).isEqualTo(1);
        assertThat(count("skip.no_endpoint_" + DPI)).isEqualTo(1);
        assertThat(count("impression.reported_" + DPI)).isEqualTo(1);
        assertThat(count("impression.error_" + DPI)).isEqualTo(1);
    }

    @Test
    public void shouldRecordNothingWhenDisabled() {
        // given
        final IntentiqIdentityMetrics disabled = new NoopIntentiqIdentityMetrics();

        // when
        disabled.cacheHit("l1", "third_party", DPI);
        disabled.terminationCause(20, DPI);
        disabled.apiLatency(1_000_000L, DPI);

        // then
        assertThat(metricRegistry.getMetrics()).isEmpty();
    }

    @Test
    public void shouldOmitDpiSuffixWhenDpiBlank() {
        // when
        target.enriched(null);
        target.enriched("");

        // then
        assertThat(count("enriched")).isEqualTo(2);
    }

    @Test
    public void shouldIncrementTerminationCauseCounterByRawCauseId() {
        // when
        target.terminationCause(20, DPI);
        target.terminationCause(20, DPI);
        target.terminationCause(36, DPI);

        // then
        assertThat(count("tc.20_" + DPI)).isEqualTo(2);
        assertThat(count("tc.36_" + DPI)).isEqualTo(1);
    }

    @Test
    public void shouldDropOutOfRangeTerminationCauses() {
        // when — out-of-range values emit no counter at all
        target.terminationCause(3333, DPI);
        target.terminationCause(120088, DPI);
        target.terminationCause(49, DPI);

        // then
        assertThat(count("tc.3333_" + DPI)).isZero();
        assertThat(count("tc.120088_" + DPI)).isZero();
        assertThat(count("tc.49_" + DPI)).isEqualTo(1);
    }

    @Test
    public void shouldRecordApiLatencyTimer() {
        // when
        target.apiLatency(1_000_000L, DPI);
        target.apiLatency(3_000_000L, DPI);

        // then
        assertThat(metricRegistry.timer("modules.module.intentiq-identity.custom.api.latency_" + DPI).getCount())
                .isEqualTo(2);
    }

    @Test
    public void shouldRecordL1L2HealthMetricsGloballyWithoutDpiSuffix() {
        // when — shared L1/L2 health metrics carry no _<dpi> segment
        target.l1GetError();
        target.l1PutError();
        target.l2GetError();
        target.l2PutError();
        target.l2PutError();
        target.l2GetLatency(1_000_000L);
        target.l2PutLatency(2_000_000L);
        target.registerL1Gauges(() -> 7L, () -> 3L);
        target.registerL2Gauges(() -> 100L, () -> 9L);

        // then
        assertThat(count("l1.get.error")).isEqualTo(1);
        assertThat(count("l1.put.error")).isEqualTo(1);
        assertThat(count("l2.get.error")).isEqualTo(1);
        assertThat(count("l2.put.error")).isEqualTo(2);
        assertThat(metricRegistry.timer("modules.module.intentiq-identity.custom.l2.get.latency").getCount())
                .isEqualTo(1);
        assertThat(metricRegistry.timer("modules.module.intentiq-identity.custom.l2.put.latency").getCount())
                .isEqualTo(1);
        assertThat(gauge("l1.size")).isEqualTo(7L);
        assertThat(gauge("l1.eviction")).isEqualTo(3L);
        assertThat(gauge("l2.size")).isEqualTo(100L);
        assertThat(gauge("l2.eviction")).isEqualTo(9L);
    }

    @Test
    public void shouldRecordNoHealthMetricsWhenDisabled() {
        // given
        final IntentiqIdentityMetrics disabled = new NoopIntentiqIdentityMetrics();

        // when
        disabled.l2GetError();
        disabled.l1PutError();
        disabled.l2GetLatency(1_000_000L);
        disabled.registerL1Gauges(() -> 7L, () -> 3L);

        // then
        assertThat(metricRegistry.getMetrics()).isEmpty();
    }

    private Object gauge(String name) {
        return metricRegistry.getGauges().get("modules.module.intentiq-identity.custom." + name).getValue();
    }

    private long count(String name) {
        return metricRegistry.counter("modules.module.intentiq-identity.custom." + name).getCount();
    }
}
