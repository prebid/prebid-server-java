package org.prebid.server.hooks.modules.intentiq.identity.metric;

import com.codahale.metrics.Gauge;
import com.codahale.metrics.MetricRegistry;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.hooks.modules.intentiq.identity.v1.IntentiqIdentityModule;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Module-specific counters for dashboards, namespaced under {@code modules.module.<code>.custom.*}
 * (grouped with the framework's {@code modules.module.<code>.*} tree, see the README Metrics section).
 * Call/success/failure/execution-time are already emitted by the hook framework and are not duplicated here.
 *
 * <p>Each metric is suffixed with the partner's {@code dpi} (the partner-facing data-provider id, never an
 * internal backend id) as {@code <name>_<dpi>}, following the per-partner naming convention so the same
 * Grafana per-partner templating applies. The suffix is omitted when no dpi is configured.
 */
public class IntentiqIdentityMetrics {

    private static final String PREFIX = "modules.module." + IntentiqIdentityModule.CODE + ".custom.";

    private final MetricRegistry metricRegistry;

    public IntentiqIdentityMetrics(MetricRegistry metricRegistry) {
        this.metricRegistry = Objects.requireNonNull(metricRegistry);
    }

    /** For the no-op subclass wired when {@code metrics-enabled} is false; never touches the registry. */
    protected IntentiqIdentityMetrics() {
        this.metricRegistry = null;
    }

    /** Positive entry served from cache; {@code layer} is {@code l1} (Caffeine) or {@code l2} (Redis). */
    public void cacheHit(String layer, String keyType, String dpi) {
        inc("cache." + layer + ".hit." + keyType, dpi);
    }

    /** Full miss — neither L1 nor L2 had the id; the API is called. Not layer-specific. */
    public void cacheMiss(String keyType, String dpi) {
        inc("cache.miss." + keyType, dpi);
    }

    public void cacheNegativeHit(String layer, String keyType, String dpi) {
        inc("cache." + layer + ".negative.hit." + keyType, dpi);
    }

    public void cacheInProgress(String layer, String keyType, String dpi) {
        inc("cache." + layer + ".in_progress." + keyType, dpi);
    }

    public void apiSuccess(String dpi) {
        inc("api.success", dpi);
    }

    public void apiError(String dpi) {
        inc("api.error", dpi);
    }

    public void enriched(String dpi) {
        inc("enriched", dpi);
    }

    /** The resolution produced no eids (no match); pairs with {@link #enriched} for a match rate. */
    public void eidsNone(String dpi) {
        inc("eids.none", dpi);
    }

    /** Resolution skipped before any API call because no api-endpoint is configured for the partner. */
    public void skipNoEndpoint(String dpi) {
        inc("skip.no_endpoint", dpi);
    }

    public void terminationCause(long tc, String dpi) {
        if (tc >= 0 && tc < 200) {
            inc("tc." + tc, dpi);
        }
    }

    /** Records the latency of a single identity-resolution API call, regardless of outcome. */
    public void apiLatency(long timeNanos, String dpi) {
        time("api.latency", dpi, timeNanos);
    }

    /**
     * Wall-clock latency of the whole module flow within one auction: from the enrich hook
     * (processed-auction-request) entry to the auction-response hook (bid release). Recorded once
     * per auction the module participated in.
     */
    public void flowLatency(long timeNanos, String dpi) {
        time("flow.latency", dpi, timeNanos);
    }

    public void impressionReported(String dpi) {
        inc("impression.reported", dpi);
    }

    public void impressionError(String dpi) {
        inc("impression.error", dpi);
    }

    // --- Backpressure / capacity (shared L1/L2 health) ---------------------------------------------
    // Caffeine (L1) and Redis (L2) are process-wide singletons shared across all partners, so their
    // saturation is not attributable to a single dpi. These are emitted WITHOUT the _<dpi> suffix
    // (global), unlike the per-partner business counters above; this also keeps their cardinality at
    // exactly one series each. See the README Metrics section.

    /** An L1 (Caffeine) read threw; treated as a miss for that key. Should be ~never. */
    public void l1GetError() {
        inc("l1.get.error", null);
    }

    /** An L1 (Caffeine) write threw; the entry did not land in L1. Should be ~never. */
    public void l1PutError() {
        inc("l1.put.error", null);
    }

    /** Latency of a single L2 (Redis) GET, recorded on every probe regardless of outcome. */
    public void l2GetLatency(long timeNanos) {
        time("l2.get.latency", null, timeNanos);
    }

    /** Latency of a single L2 (Redis) PUT, recorded on every write regardless of outcome. */
    public void l2PutLatency(long timeNanos) {
        time("l2.put.latency", null, timeNanos);
    }

    /**
     * An L2 GET failed/timed out (connection refused, pool exhaustion, command timeout). The cache
     * fails open — it falls through to a live API call — so without this counter the failure is
     * invisible. Each fall-through-on-read is one increment.
     */
    public void l2GetError() {
        inc("l2.get.error", null);
    }

    /** An L2 PUT failed/timed out; the entry still lives in L1 but did not reach the shared store. */
    public void l2PutError() {
        inc("l2.put.error", null);
    }

    /**
     * Register the L1 (Caffeine) capacity gauges. Idempotent and global (no dpi). {@code size}
     * tracks current entry count against the configured {@code cache-max-size}; {@code evictions}
     * is Caffeine's cumulative eviction count (rate it in the dashboard). No-op when recording is
     * disabled. ({@code load.failure} is intentionally not exposed — this cache uses manual
     * {@code put} with no {@code CacheLoader}, so load failures cannot occur.)
     */
    public void registerL1Gauges(LongSupplier size, LongSupplier evictions) {
        gauge("l1.size", size);
        gauge("l1.eviction", evictions);
    }

    /**
     * Register the L2 (Redis) capacity gauges, fed by a periodic poller (Redis stats can't be read
     * synchronously). Global (no dpi). {@code size} is {@code DBSIZE}, {@code evictions} is the
     * cumulative {@code evicted_keys} from {@code INFO stats} — both Redis-instance-wide, not
     * module-scoped. No-op when recording is disabled.
     */
    public void registerL2Gauges(LongSupplier size, LongSupplier evictions) {
        gauge("l2.size", size);
        gauge("l2.eviction", evictions);
    }

    protected void inc(String name, String dpi) {
        metricRegistry.counter(withDpi(name, dpi)).inc();
    }

    protected void time(String name, String dpi, long timeNanos) {
        metricRegistry.timer(withDpi(name, dpi)).update(timeNanos, TimeUnit.NANOSECONDS);
    }

    protected void gauge(String name, LongSupplier supplier) {
        metricRegistry.gauge(withDpi(name, null), () -> (Gauge<Long>) supplier::getAsLong);
    }

    private static String withDpi(String name, String dpi) {
        final String full = PREFIX + name;
        return StringUtils.isNotBlank(dpi) ? full + "_" + dpi : full;
    }
}
