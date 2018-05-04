package org.prebid.server.settings;

import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.TriFunction;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Adds caching functionality for {@link ApplicationSettings} implementation
 */
public class CachingApplicationSettings implements ApplicationSettings {

    private final ApplicationSettings delegate;

    private final Map<String, Account> accountCache;
    private final Map<String, String> adUnitConfigCache;
    private final SettingsCache cache;
    private final SettingsCache ampCache;

    public CachingApplicationSettings(ApplicationSettings delegate, SettingsCache cache, SettingsCache ampCache,
                                      int ttl, int size) {
        if (ttl <= 0 || size <= 0) {
            throw new IllegalArgumentException("ttl and size must be positive");
        }
        this.delegate = Objects.requireNonNull(delegate);
        this.accountCache = SettingsCache.createCache(ttl, size);
        this.adUnitConfigCache = SettingsCache.createCache(ttl, size);
        this.cache = Objects.requireNonNull(cache);
        this.ampCache = Objects.requireNonNull(ampCache);
    }

    /**
     * Retrieves account from cache or delegates it to original fetcher.
     */
    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return getFromCacheOrDelegate(accountCache, accountId, timeout, delegate::getAccountById);
    }

    /**
     * Retrieves adUnit config from cache or delegates it to original fetcher.
     */
    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId, Timeout timeout) {
        return getFromCacheOrDelegate(adUnitConfigCache, adUnitConfigId, timeout, delegate::getAdUnitConfigById);
    }

    /**
     * Retrieves stored data from cache or delegates it to original fetcher.
     */
    @Override
    public Future<StoredDataResult> getStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return getFromCacheOrDelegate(cache, requestIds, impIds, timeout, delegate::getStoredData);
    }

    /**
     * Retrieves amp stored data from cache or delegates it to original fetcher.
     */
    @Override
    public Future<StoredDataResult> getAmpStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return getFromCacheOrDelegate(ampCache, requestIds, impIds, timeout, delegate::getAmpStoredData);
    }

    private static <T> Future<T> getFromCacheOrDelegate(Map<String, T> cache, String key, Timeout timeout,
                                                        BiFunction<String, Timeout, Future<T>> retriever) {
        final Future<T> result;

        final T cachedValue = cache.get(key);
        if (cachedValue != null) {
            result = Future.succeededFuture(cachedValue);
        } else {
            result = retriever.apply(key, timeout)
                    .map(value -> {
                        cache.put(key, value);
                        return value;
                    });
        }

        return result;
    }

    /**
     * Retrieves stored data from cache and collects ids which were absent. For absent ids makes look up to original
     * source, combines results and updates cache with missed stored request. In case when origin source returns Failed
     * {@link Future} propagates its result to caller. In successive call return {@link Future&lt;StoredDataResult&gt;}
     * with all found stored requests and error from origin source id call was made.
     */
    private static Future<StoredDataResult> getFromCacheOrDelegate(
            SettingsCache cache, Set<String> requestIds, Set<String> impIds, Timeout timeout,
            TriFunction<Set<String>, Set<String>, Timeout, Future<StoredDataResult>> retriever) {

        final Map<String, String> requestCache = cache.getRequestCache();
        final Map<String, String> impCache = cache.getImpCache();

        final Set<String> missedRequestIds = new HashSet<>();
        final Map<String, String> storedIdToRequest = getFromCacheOrAddMissedIds(requestIds, requestCache,
                missedRequestIds);

        final Set<String> missedImpIds = new HashSet<>();
        final Map<String, String> storedIdToImp = getFromCacheOrAddMissedIds(impIds, impCache, missedImpIds);

        if (missedRequestIds.isEmpty() && missedImpIds.isEmpty()) {
            return Future.succeededFuture(
                    StoredDataResult.of(storedIdToRequest, storedIdToImp, Collections.emptyList()));
        }

        // delegate call to original source for missed ids and update cache with it
        return retriever.apply(missedRequestIds, missedImpIds, timeout).compose(result -> {
            final Map<String, String> storedIdToRequestFromDelegate = result.getStoredIdToRequest();
            final Map<String, String> storedIdToImpFromDelegate = result.getStoredIdToImp();

            cache.save(storedIdToRequestFromDelegate, storedIdToImpFromDelegate);

            storedIdToRequest.putAll(storedIdToRequestFromDelegate);
            storedIdToImp.putAll(storedIdToImpFromDelegate);

            return Future.succeededFuture(StoredDataResult.of(storedIdToRequest, storedIdToImp, result.getErrors()));
        });
    }

    private static Map<String, String> getFromCacheOrAddMissedIds(Set<String> ids, Map<String, String> cache,
                                                                  Set<String> missedIds) {
        final Map<String, String> storedIdToJson = new HashMap<>(ids.size());
        for (String id : ids) {
            final String cachedValue = cache.get(id);
            if (cachedValue != null) {
                storedIdToJson.put(id, cachedValue);
            } else {
                missedIds.add(id);
            }
        }
        return storedIdToJson;
    }
}
