package org.prebid.server.settings;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.settings.model.StoredItem;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Just a simple wrapper over in-memory caches for requests and imps.
 */
public class SettingsCache implements CacheNotificationListener {

    private final Map<String, Set<StoredItem>> requestCache;
    private final Map<String, Set<StoredItem>> impCache;

    public SettingsCache(int ttl, int size) {
        if (ttl <= 0 || size <= 0) {
            throw new IllegalArgumentException("ttl and size must be positive");
        }
        requestCache = createCache(ttl, size);
        impCache = createCache(ttl, size);
    }

    static <T> Map<String, T> createCache(int ttl, int size) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl, TimeUnit.SECONDS)
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
                                        String accountId, String id, String value) {
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
        requestCache.keySet().removeAll(requests);
        impCache.keySet().removeAll(imps);
    }
}
