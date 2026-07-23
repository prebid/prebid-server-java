package org.prebid.server.hooks.modules.intentiq.identity.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.iab.openrtb.request.Eid;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.prebid.server.hooks.modules.intentiq.identity.metric.IntentiqIdentityMetrics;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dual-layer, multi-key (alias) cache for resolved eids: Caffeine (L1, in-process) backed by a
 * pluggable {@link IdentityStore} (L2, shared; Redis by default).
 *
 * <p>A request yields an ordered list of {@link CacheKey}s (one per first-party id present). On read,
 * the highest-priority key with a live entry wins and that entry is back-filled under every other key
 * that missed, so the alias graph grows over time and a later request carrying any of those ids hits
 * (strategy B). On a full miss the caller fetches once and writes the entry under all keys.
 *
 * <p>Entries carry the IntentIQ {@code cttl} capped by a per-{@link KeyType} ceiling (see
 * {@link CacheTtlPolicy}). Unresolvable ids are cached as a short-lived negative sentinel so they do
 * not re-hit the upstream API. L2 failures are swallowed (fail-open) so the auction can fall through
 * to a live call. Differing resolutions are never merged — only the single winning entry propagates.
 */
public class IdentityCache {

    private static final Logger logger = LoggerFactory.getLogger(IdentityCache.class);

    private final Cache<String, CacheEntry> local;
    private final IdentityStore store;
    private final JacksonMapper mapper;
    private final CacheTtlPolicy ttlPolicy;
    private final IntentiqIdentityMetrics metrics;

    public IdentityCache(long maxSize, CacheTtlPolicy ttlPolicy, IdentityStore store, JacksonMapper mapper,
                         IntentiqIdentityMetrics metrics) {
        this.local = Caffeine.newBuilder()
                .maximumSize(maxSize)
                .expireAfter(new CacheEntryExpiry())
                .recordStats()
                .build();
        this.ttlPolicy = Objects.requireNonNull(ttlPolicy);
        this.store = Objects.requireNonNull(store);
        this.mapper = Objects.requireNonNull(mapper);
        this.metrics = Objects.requireNonNull(metrics);
        // L1 capacity gauges: current size (vs cache-max-size) and cumulative evictions.
        metrics.registerL1Gauges(local::estimatedSize, () -> local.stats().evictionCount());
    }

    public Future<CacheResult> get(List<CacheKey> keys) {
        if (keys == null || keys.isEmpty()) {
            return Future.succeededFuture(CacheResult.miss());
        }

        // L1 sweep in priority order; Caffeine evicts expired entries, so a present entry is live.
        // A resolved entry always wins; an in-progress marker is a fallback that short-circuits the
        // L2 probe (this instance already knows a call is in flight) without firing a duplicate.
        KeyType inProgressType = null;
        for (int i = 0; i < keys.size(); i++) {
            final CacheEntry entry = l1Get(keys.get(i).key());
            if (entry == null) {
                continue;
            }
            if (entry.isInProgress()) {
                if (inProgressType == null) {
                    inProgressType = keys.get(i).type();
                }
                continue;
            }
            backfill(keys, i, entry);
            return Future.succeededFuture(toResult(entry, keys.get(i).type(), CacheResult.Layer.L1));
        }
        if (inProgressType != null) {
            return Future.succeededFuture(CacheResult.inProgress(inProgressType, CacheResult.Layer.L1));
        }

        // Full L1 miss: probe all keys in L2 concurrently. Prefer the highest-priority resolved entry;
        // fall back to an in-progress marker only if no resolved entry is found under any key.
        return l2GetAll(keys).map(entries -> {
            KeyType l2InProgressType = null;
            for (int i = 0; i < entries.size(); i++) {
                final CacheEntry entry = entries.get(i);
                if (entry == null) {
                    continue;
                }
                if (entry.isInProgress()) {
                    if (l2InProgressType == null) {
                        l1Put(keys.get(i).key(), entry);
                        l2InProgressType = keys.get(i).type();
                    }
                    continue;
                }
                l1Put(keys.get(i).key(), entry);
                backfill(keys, i, entry);
                return toResult(entry, keys.get(i).type(), CacheResult.Layer.L2);
            }
            return l2InProgressType != null
                    ? CacheResult.inProgress(l2InProgressType, CacheResult.Layer.L2)
                    : CacheResult.miss();
        });
    }

    public void put(List<CacheKey> keys, List<Eid> eids, long cttlMs) {
        for (CacheKey key : keys) {
            final long ttl = ttlPolicy.effectiveTtlMs(key.type(), cttlMs);
            writeBoth(key.key(), CacheEntry.of(eids, false, false, System.currentTimeMillis() + ttl), ttl);
        }
    }

    public void putNegative(List<CacheKey> keys, long cttlMs) {
        final long ttl = ttlPolicy.negativeTtlMs(cttlMs);
        for (CacheKey key : keys) {
            writeBoth(key.key(), CacheEntry.of(List.of(), true, false, System.currentTimeMillis() + ttl), ttl);
        }
    }

    /**
     * Mark a resolution as in flight: write an IN_PROGRESS sentinel under every key with the short
     * in-progress TTL, so a concurrent request for the same id reads it and skips firing a duplicate
     * upstream call. Overwritten by {@link #put} / {@link #putNegative} when the call completes; if the
     * call never completes the marker simply expires.
     */
    public void putInProgress(List<CacheKey> keys) {
        final long ttl = ttlPolicy.inProgressTtlMs();
        for (CacheKey key : keys) {
            writeBoth(key.key(), CacheEntry.of(List.of(), false, true, System.currentTimeMillis() + ttl), ttl);
        }
    }

    /** Propagate the winning entry under every other key that missed, capped by each key's ceiling. */
    private void backfill(List<CacheKey> keys, int hitIndex, CacheEntry hit) {
        final long remaining = hit.getExp() - System.currentTimeMillis();
        if (remaining <= 0) {
            return;
        }

        for (int i = 0; i < keys.size(); i++) {
            if (i == hitIndex) {
                continue;
            }
            final CacheKey key = keys.get(i);
            if (l1Get(key.key()) != null) {
                continue;
            }
            final long ttl = Math.min(remaining, ttlPolicy.ceilingFor(key.type()));
            writeBoth(key.key(), CacheEntry.of(hit.getEids(), hit.isNegative(), hit.isInProgress(),
                    System.currentTimeMillis() + ttl), ttl);
        }
    }

    private void writeBoth(String key, CacheEntry entry, long ttlMs) {
        l1Put(key, entry);
        final long startNanos = System.nanoTime();
        store.put(key, mapper.encodeToString(entry), ttlMs)
                .onComplete(ignored -> metrics.l2PutLatency(System.nanoTime() - startNanos))
                .onFailure(throwable -> {
                    metrics.l2PutError();
                    logger.warn("IntentIQ identity cache L2 PUT failed", throwable);
                });
    }

    // L1 (Caffeine) is in-process and effectively never fails, but wrap it so a pathological failure
    // (e.g. expiry/weigher throwing, OOM) is counted and swallowed (fail open) rather than aborting.
    private void l1Put(String key, CacheEntry entry) {
        try {
            local.put(key, entry);
        } catch (RuntimeException e) {
            metrics.l1PutError();
            logger.warn("IntentIQ identity cache L1 PUT failed", e);
        }
    }

    private CacheEntry l1Get(String key) {
        try {
            return local.getIfPresent(key);
        } catch (RuntimeException e) {
            metrics.l1GetError();
            logger.warn("IntentIQ identity cache L1 GET failed", e);
            return null;
        }
    }

    private Future<List<CacheEntry>> l2GetAll(List<CacheKey> keys) {
        final List<Future<CacheEntry>> futures = new ArrayList<>();
        for (CacheKey key : keys) {
            final long startNanos = System.nanoTime();
            futures.add(store.get(key.key())
                    .onComplete(ignored -> metrics.l2GetLatency(System.nanoTime() - startNanos))
                    .map(this::decodeValid)
                    .otherwise(throwable -> {
                        // L2 read failed: we fall through to a live API call (fail open). Count it so the
                        // otherwise-swallowed failure is visible for alerting.
                        metrics.l2GetError();
                        logger.warn("IntentIQ identity cache L2 GET failed", throwable);
                        return null;
                    }));
        }
        return Future.join(futures).map(CompositeFuture::list);
    }

    private CacheEntry decodeValid(String value) {
        if (value == null) {
            return null;
        }
        final CacheEntry entry = mapper.decodeValue(value, CacheEntry.class);
        if (entry == null || entry.getExp() <= System.currentTimeMillis()) {
            return null;
        }
        return entry;
    }

    private static CacheResult toResult(CacheEntry entry, KeyType keyType, CacheResult.Layer layer) {
        if (entry.isInProgress()) {
            return CacheResult.inProgress(keyType, layer);
        }
        return entry.isNegative()
                ? CacheResult.negative(keyType, layer)
                : CacheResult.hit(entry.getEids(), keyType, layer);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor(staticName = "of")
    static class CacheEntry {

        List<Eid> eids;

        boolean negative;

        boolean inProgress;

        long exp;
    }

    private static class CacheEntryExpiry implements Expiry<String, CacheEntry> {

        @Override
        public long expireAfterCreate(@NonNull String key, @NonNull CacheEntry value, long currentTime) {
            return remainingNanos(value);
        }

        @Override
        public long expireAfterUpdate(@NonNull String key, @NonNull CacheEntry value,
                                      long currentTime, long currentDuration) {
            return remainingNanos(value);
        }

        @Override
        public long expireAfterRead(@NonNull String key, @NonNull CacheEntry value,
                                    long currentTime, long currentDuration) {
            return currentDuration;
        }

        private static long remainingNanos(CacheEntry value) {
            return Math.max(0, value.getExp() - System.currentTimeMillis()) * 1_000_000L;
        }
    }
}
