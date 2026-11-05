package org.prebid.server.hooks.modules.intentiq.identity.cache;

import io.vertx.core.Vertx;
import org.prebid.server.hooks.modules.intentiq.identity.metric.IntentiqIdentityMetrics;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Periodically polls Redis (L2) {@code DBSIZE} and {@code INFO stats} {@code evicted_keys} and exposes
 * them as the global {@code l2.size} / {@code l2.eviction} gauges. Redis stats are asynchronous and
 * instance-wide, so (unlike Caffeine's in-process L1 counters) they can't be read inside a synchronous
 * gauge — this caches the latest poll into atomics the gauges read. Both values are Redis-instance-wide,
 * not module-scoped.
 */
public class RedisStatsReporter {

    private static final Logger logger = LoggerFactory.getLogger(RedisStatsReporter.class);

    private final RedisIdentityStore store;
    private final Vertx vertx;
    private final long pollIntervalMs;
    private final AtomicLong size = new AtomicLong();
    private final AtomicLong evictions = new AtomicLong();

    public RedisStatsReporter(RedisIdentityStore store,
                              Vertx vertx,
                              IntentiqIdentityMetrics metrics,
                              long pollIntervalMs) {
        this.store = Objects.requireNonNull(store);
        this.vertx = Objects.requireNonNull(vertx);
        this.pollIntervalMs = pollIntervalMs;
        metrics.registerL2Gauges(size::get, evictions::get);
    }

    /** Poll once immediately, then on a fixed interval. Returns this for fluent wiring. */
    public RedisStatsReporter start() {
        poll();
        vertx.setPeriodic(pollIntervalMs, id -> poll());
        return this;
    }

    private void poll() {
        store.dbSize()
                .onSuccess(size::set)
                .onFailure(t -> logger.warn("IntentIQ identity L2 DBSIZE poll failed", t));
        store.evictedKeys()
                .onSuccess(evictions::set)
                .onFailure(t -> logger.warn("IntentIQ identity L2 evicted_keys poll failed", t));
    }
}
