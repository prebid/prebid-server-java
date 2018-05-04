package org.prebid.server.settings;

import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Just a simple wrapper over in-memory caches for requests and imps.
 */
public class SettingsCache {

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

    public Map<String, String> getRequestCache() {
        return requestCache;
    }

    public Map<String, String> getImpCache() {
        return impCache;
    }
}
