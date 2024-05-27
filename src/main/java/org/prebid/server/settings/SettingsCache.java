package org.prebid.server.settings;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.checkerframework.checker.index.qual.NonNegative;
import org.prebid.server.settings.model.StoredItem;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Just a simple wrapper over in-memory caches for requests and imps.
 */
public class SettingsCache implements CacheNotificationListener {

    private final Map<String, Set<StoredItem>> requestCache;
    private final Map<String, Set<StoredItem>> impCache;

    public SettingsCache(int ttl, int size, int jitter) {
        if (ttl <= 0 || size <= 0) {
            throw new IllegalArgumentException("ttl and size must be positive");
        }
        if (jitter < 0 || jitter >= ttl) {
            throw new IllegalArgumentException("jitter must match the inequality: 0 <= jitter < ttl");
        }

        requestCache = createCache(ttl, size, jitter);
        impCache = createCache(ttl, size, jitter);
    }

    public static <T> Map<String, T> createCache(int ttlSeconds, int size, int jitterSeconds) {
        final long expireAfterNanos = (long) (ttlSeconds * 1e9);
        final long jitterNanos = jitterSeconds == 0 ? 0L : (long) (jitterSeconds * 1e9);

        return Caffeine.newBuilder()
                .expireAfter(jitterNanos == 0L
                        ? new StaticExpiry<>(expireAfterNanos)
                        : new ExpiryWithJitter<>(expireAfterNanos, jitterNanos))
                .maximumSize(size)
                .<String, T>build()
                .asMap();
    }

    Map<String, Set<StoredItem>> getRequestCache() {
        return requestCache;
    }

    Map<String, Set<StoredItem>> getImpCache() {
        return impCache;
    }

    void saveRequestCache(String accountId, String requestId, String requestValue) {
        saveCachedValue(requestCache, accountId, requestId, requestValue);
    }

    void saveImpCache(String accountId, String impId, String impValue) {
        saveCachedValue(impCache, accountId, impId, impValue);
    }

    private static void saveCachedValue(Map<String, Set<StoredItem>> cache,
                                        String accountId,
                                        String id,
                                        String value) {

        final Set<StoredItem> values = ObjectUtils.defaultIfNull(cache.get(id), new HashSet<>());
        values.add(StoredItem.of(accountId, value));
        cache.put(id, values);
    }

    /**
     * Saves given stored requests and imps for NULL account.
     * <p>
     * TODO: account should be added to all services uses this method
     */
    @Override
    public void save(Map<String, String> requests, Map<String, String> imps) {
        if (MapUtils.isNotEmpty(requests)) {
            requests.forEach((key, value) -> requestCache.put(key, Collections.singleton(StoredItem.of(null, value))));
        }
        if (MapUtils.isNotEmpty(imps)) {
            imps.forEach((key, value) -> impCache.put(key, Collections.singleton(StoredItem.of(null, value))));
        }
    }

    @Override
    public void invalidate(List<String> requests, List<String> imps) {
        requests.forEach(requestCache.keySet()::remove);
        imps.forEach(impCache.keySet()::remove);
    }

    private static class StaticExpiry<K, V> implements Expiry<K, V> {

        private final long expireAfterNanos;

        private StaticExpiry(long expireAfterNanos) {
            this.expireAfterNanos = expireAfterNanos;
        }

        @Override
        public long expireAfterCreate(K key, V value, long currentTime) {
            return expireAfterNanos;
        }

        @Override
        public long expireAfterUpdate(K key, V value, long currentTime, @NonNegative long currentDuration) {
            return expireAfterNanos;
        }

        @Override
        public long expireAfterRead(K key, V value, long currentTime, @NonNegative long currentDuration) {
            return currentDuration;
        }
    }

    private static class ExpiryWithJitter<K, V> implements Expiry<K, V> {

        private final Expiry<K, V> baseExpiry;
        private final long jitterNanos;

        private ExpiryWithJitter(long baseExpireAfterNanos, long jitterNanos) {
            this.baseExpiry = new StaticExpiry<>(baseExpireAfterNanos);
            this.jitterNanos = jitterNanos;
        }

        @Override
        public long expireAfterCreate(K key, V value, long currentTime) {
            return baseExpiry.expireAfterCreate(key, value, currentTime) + jitter();
        }

        @Override
        public long expireAfterUpdate(K key, V value, long currentTime, @NonNegative long currentDuration) {
            return baseExpiry.expireAfterUpdate(key, value, currentTime, currentDuration) + jitter();
        }

        @Override
        public long expireAfterRead(K key, V value, long currentTime, @NonNegative long currentDuration) {
            return baseExpiry.expireAfterRead(key, value, currentTime, currentDuration);
        }

        private long jitter() {
            return ThreadLocalRandom.current().nextLong(-jitterNanos, jitterNanos);
        }
    }
}
