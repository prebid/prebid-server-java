package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.helper.StoredDataFetcher;
import org.prebid.server.settings.helper.StoredItemResolver;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredItem;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Adds caching functionality for {@link ApplicationSettings} implementation.
 */
public class CachingApplicationSettings implements ApplicationSettings {

    private static final Logger logger = LoggerFactory.getLogger(CachingApplicationSettings.class);

    private final ApplicationSettings delegate;

    private final Map<String, Account> accountCache;
    private final Map<String, String> accountToErrorCache;
    private final Map<String, String> adServerPublisherToErrorCache;
    private final Map<String, Map<String, String>> categoryConfigCache;
    private final SettingsCache cache;
    private final SettingsCache ampCache;
    private final SettingsCache videoCache;
    private final Metrics metrics;

    public CachingApplicationSettings(ApplicationSettings delegate,
                                      SettingsCache cache,
                                      SettingsCache ampCache,
                                      SettingsCache videoCache,
                                      Metrics metrics,
                                      int ttl,
                                      int size) {

        if (ttl <= 0 || size <= 0) {
            throw new IllegalArgumentException("ttl and size must be positive");
        }
        this.delegate = Objects.requireNonNull(delegate);
        this.accountCache = SettingsCache.createCache(ttl, size);
        this.accountToErrorCache = SettingsCache.createCache(ttl, size);
        this.adServerPublisherToErrorCache = SettingsCache.createCache(ttl, size);
        this.categoryConfigCache = SettingsCache.createCache(ttl, size);
        this.cache = Objects.requireNonNull(cache);
        this.ampCache = Objects.requireNonNull(ampCache);
        this.videoCache = Objects.requireNonNull(videoCache);
        this.metrics = Objects.requireNonNull(metrics);
    }

    /**
     * Retrieves account from cache or delegates it to original fetcher.
     */
    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return getFromCacheOrDelegate(
                accountCache,
                accountToErrorCache,
                accountId,
                timeout,
                delegate::getAccountById,
                event -> metrics.updateSettingsCacheEventMetric(MetricName.account, event));
    }

    /**
     * Retrieves stored data from cache or delegates it to original fetcher.
     */
    @Override
    public Future<StoredDataResult> getStoredData(String accountId,
                                                  Set<String> requestIds,
                                                  Set<String> impIds,
                                                  Timeout timeout) {

        return getFromCacheOrDelegate(cache, accountId, requestIds, impIds, timeout, delegate::getStoredData);
    }

    /**
     * Retrieves amp stored data from cache or delegates it to original fetcher.
     */
    @Override
    public Future<StoredDataResult> getAmpStoredData(String accountId,
                                                     Set<String> requestIds,
                                                     Set<String> impIds,
                                                     Timeout timeout) {

        return getFromCacheOrDelegate(ampCache, accountId, requestIds, impIds, timeout, delegate::getAmpStoredData);
    }

    @Override
    public Future<StoredDataResult> getVideoStoredData(String accountId,
                                                       Set<String> requestIds,
                                                       Set<String> impIds,
                                                       Timeout timeout) {

        return getFromCacheOrDelegate(videoCache, accountId, requestIds, impIds, timeout, delegate::getVideoStoredData);
    }

    /**
     * Delegates stored response retrieve to original fetcher, as caching is not supported fot stored response.
     */
    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        return delegate.getStoredResponses(responseIds, timeout);
    }

    @Override
    public Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout) {
        final String compoundKey = StringUtils.isNotBlank(publisher)
                ? String.format("%s_%s", primaryAdServer, publisher)
                : primaryAdServer;

        return getFromCacheOrDelegate(categoryConfigCache, adServerPublisherToErrorCache, compoundKey, timeout,
                (key, timeoutParam) -> delegate.getCategories(primaryAdServer, publisher, timeout),
                CachingApplicationSettings::noOp);
    }

    private static <T> Future<T> getFromCacheOrDelegate(Map<String, T> cache,
                                                        Map<String, String> accountToErrorCache,
                                                        String key,
                                                        Timeout timeout,
                                                        BiFunction<String, Timeout, Future<T>> retriever,
                                                        Consumer<MetricName> metricUpdater) {

        final T cachedValue = cache.get(key);
        if (cachedValue != null) {
            metricUpdater.accept(MetricName.hit);

            return Future.succeededFuture(cachedValue);
        }

        metricUpdater.accept(MetricName.miss);

        final String preBidExceptionMessage = accountToErrorCache.get(key);
        if (preBidExceptionMessage != null) {
            return Future.failedFuture(new PreBidException(preBidExceptionMessage));
        }

        return retriever.apply(key, timeout)
                .map(value -> {
                    cache.put(key, value);
                    return value;
                })
                .recover(throwable -> cacheAndReturnFailedFuture(throwable, key, accountToErrorCache));
    }

    /**
     * Retrieves stored data from cache and collects ids which were absent. For absent ids makes look up to original
     * source, combines results and updates cache with missed stored item. In case when origin source returns failed
     * {@link Future} propagates its result to caller. In successive call return {@link Future&lt;StoredDataResult&gt;}
     * with all found stored items and error from origin source id call was made.
     */
    private static Future<StoredDataResult> getFromCacheOrDelegate(
            SettingsCache cache,
            String accountId,
            Set<String> requestIds,
            Set<String> impIds,
            Timeout timeout,
            StoredDataFetcher<String, Set<String>, Set<String>, Timeout, Future<StoredDataResult>> retriever) {

        // empty string account ID doesn't make sense
        final String normalizedAccountId = StringUtils.stripToNull(accountId);

        // search in cache
        final Map<String, Set<StoredItem>> requestCache = cache.getRequestCache();
        final Map<String, Set<StoredItem>> impCache = cache.getImpCache();

        final Set<String> missedRequestIds = new HashSet<>();
        final Map<String, String> storedIdToRequest = getFromCacheOrAddMissedIds(normalizedAccountId, requestIds,
                requestCache, missedRequestIds);

        final Set<String> missedImpIds = new HashSet<>();
        final Map<String, String> storedIdToImp = getFromCacheOrAddMissedIds(normalizedAccountId, impIds, impCache,
                missedImpIds);

        if (missedRequestIds.isEmpty() && missedImpIds.isEmpty()) {
            return Future.succeededFuture(
                    StoredDataResult.of(storedIdToRequest, storedIdToImp, Collections.emptyList()));
        }

        // delegate call to original source for missed ids and update cache with it
        return retriever.apply(normalizedAccountId, missedRequestIds, missedImpIds, timeout).map(result -> {
            final Map<String, String> storedIdToRequestFromDelegate = result.getStoredIdToRequest();
            storedIdToRequest.putAll(storedIdToRequestFromDelegate);
            for (Map.Entry<String, String> entry : storedIdToRequestFromDelegate.entrySet()) {
                cache.saveRequestCache(normalizedAccountId, entry.getKey(), entry.getValue());
            }

            final Map<String, String> storedIdToImpFromDelegate = result.getStoredIdToImp();
            storedIdToImp.putAll(storedIdToImpFromDelegate);
            for (Map.Entry<String, String> entry : storedIdToImpFromDelegate.entrySet()) {
                cache.saveImpCache(normalizedAccountId, entry.getKey(), entry.getValue());
            }

            return StoredDataResult.of(storedIdToRequest, storedIdToImp, result.getErrors());
        });
    }

    private static <T> Future<T> cacheAndReturnFailedFuture(Throwable throwable,
                                                            String key,
                                                            Map<String, String> cache) {

        if (throwable instanceof PreBidException) {
            cache.put(key, throwable.getMessage());
        }

        return Future.failedFuture(throwable);
    }

    private static Map<String, String> getFromCacheOrAddMissedIds(String accountId,
                                                                  Set<String> ids,
                                                                  Map<String, Set<StoredItem>> cache,
                                                                  Set<String> missedIds) {

        final Map<String, String> idToStoredItem = new HashMap<>(ids.size());

        for (String id : ids) {
            try {
                final StoredItem resolvedStoredItem = StoredItemResolver.resolve(null, accountId, id, cache.get(id));
                idToStoredItem.put(id, resolvedStoredItem.getData());
            } catch (PreBidException e) {
                missedIds.add(id);
            }
        }

        return idToStoredItem;
    }

    public void invalidateAccountCache(String accountId) {
        accountCache.remove(accountId);
        logger.debug("Account with id {0} was invalidated", accountId);
    }

    public void invalidateAllAccountCache() {
        accountCache.clear();
        logger.debug("All accounts cache were invalidated");
    }

    private static <ANY> void noOp(ANY any) {
    }
}
