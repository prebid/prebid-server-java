package org.prebid.server.settings;

import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Just a simple wrapper over in-memory caches for requests and imps.
 */
public class SettingsCache implements CacheNotificationListener {

    private final Map<String, String> requestCache;
    private final Map<String, String> impCache;

    public SettingsCache(int ttl, int size) {
        if (ttl <= 0 || size <= 0) {
            throw new IllegalArgumentException("ttl and size must be positive");
        }
        this.requestCache = createCache(ttl, size);
        this.impCache = createCache(ttl, size);
    }

    static <T> Map<String, T> createCache(int ttl, int size) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl, TimeUnit.SECONDS)
                .maximumSize(size)
                .<String, T>build()
                .asMap();
    }

    Map<String, String> getRequestCache() {
        return requestCache;
    }

    Map<String, String> getImpCache() {
        return impCache;
    }

    @Override
    public void save(Map<String, String> requests, Map<String, String> imps) {
        requestCache.putAll(requests);
        impCache.putAll(imps);
    }

    @Override
    public void invalidate(List<String> requests, List<String> imps) {
        requestCache.keySet().removeAll(requests);
        impCache.keySet().removeAll(imps);
    }
}
