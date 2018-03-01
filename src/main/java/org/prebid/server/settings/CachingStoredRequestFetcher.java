package org.prebid.server.settings;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.vertx.core.Future;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.settings.model.StoredRequestResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * Wrapper for storedRequestFetchers which adds cache functionality.
 */
public class CachingStoredRequestFetcher implements StoredRequestFetcher {

    private final StoredRequestFetcher delegate;
    private final Map<String, String> storedRequestCache;
    private final Map<String, String> storedAmpRequestCache;

    public CachingStoredRequestFetcher(StoredRequestFetcher delegate, int ttl, int size) {
        if (ttl <= 0 || size <= 0) {
            throw new IllegalArgumentException("ttl and size must be positive");
        }
        this.delegate = Objects.requireNonNull(delegate);
        storedRequestCache = createCache(ttl, size);
        storedAmpRequestCache = createCache(ttl, size);
    }

    /**
     * Retrieves stored requests from cache or delegates it to original storedRequestFetcher.
     */
    @Override
    public Future<StoredRequestResult> getStoredRequestsById(Set<String> ids, GlobalTimeout timeout) {
        return getFromCacheOrDelegate(storedRequestCache, ids, timeout, delegate::getStoredRequestsById);
    }

    @Override
    public Future<StoredRequestResult> getStoredRequestsByAmpId(Set<String> ids, GlobalTimeout timeout) {
        return getFromCacheOrDelegate(storedAmpRequestCache, ids, timeout, delegate::getStoredRequestsByAmpId);
    }

    private static <T> Map<String, T> createCache(int ttl, int size) {
        return Caffeine.newBuilder()
                .expireAfterWrite(ttl, TimeUnit.SECONDS)
                .maximumSize(size)
                .<String, T>build()
                .asMap();
    }

    /**
     * Retrieves stored requests from cache and collects ids which were absent. For absent ids makes look up to original
     * source, combines results and updates cache with missed stored request. In case when origin source returns Failed
     * {@link Future} propagates its result to caller. In successive call return {@link Future<StoredRequestResult>}
     * with all found stored requests and error from origin source id call was made.
     */
    private static Future<StoredRequestResult> getFromCacheOrDelegate(
            Map<String, String> cache, Set<String> ids, GlobalTimeout timeout,
            BiFunction<Set<String>, GlobalTimeout, Future<StoredRequestResult>> retriever) {

        final Map<String, String> storedRequestsFromCache = new HashMap<>();
        final Set<String> missedIds = new HashSet<>();
        for (String id : ids) {
            final String cachedStoredRequest = cache.get(id);
            if (cachedStoredRequest != null) {
                storedRequestsFromCache.put(id, cachedStoredRequest);
            } else {
                missedIds.add(id);
            }
        }

        if (missedIds.size() == 0) {
            return Future.succeededFuture(StoredRequestResult.of(storedRequestsFromCache, Collections.emptyList()));
        }

        // delegate call to original source for missed ids and update cache with it
        return retriever.apply(missedIds, timeout).compose(storedRequestResult -> {
            final Map<String, String> storedIdToJson = storedRequestResult.getStoredIdToJson();
            storedRequestsFromCache.putAll(storedIdToJson);
            cache.putAll(storedIdToJson);
            return Future.succeededFuture(
                    StoredRequestResult.of(storedRequestsFromCache, storedRequestResult.getErrors()));
        });
    }
}
