package org.prebid.server.settings;

import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.helper.StoredDataFetcher;
import org.prebid.server.settings.helper.StoredItemResolver;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.Profile;
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

public class CachingApplicationSettings implements ApplicationSettings {

    private static final Logger logger = LoggerFactory.getLogger(CachingApplicationSettings.class);

    private final ApplicationSettings delegate;

    private final Map<String, Account> accountCache;
    private final Map<String, String> accountToErrorCache;
    private final Map<String, String> adServerPublisherToErrorCache;
    private final Map<String, Map<String, String>> categoryConfigCache;
    private final SettingsCache<String> cache;
    private final SettingsCache<String> ampCache;
    private final SettingsCache<String> videoCache;
    private final SettingsCache<Profile> profileCache;
    private final Metrics metrics;

    public CachingApplicationSettings(ApplicationSettings delegate,
                                      SettingsCache<String> cache,
                                      SettingsCache<String> ampCache,
                                      SettingsCache<String> videoCache,
                                      SettingsCache<Profile> profileCache,
                                      Metrics metrics,
                                      int ttl,
                                      int size,
                                      int jitter) {

        if (ttl <= 0 || size <= 0) {
            throw new IllegalArgumentException("ttl and size must be positive");
        }
        if (jitter < 0 || jitter >= ttl) {
            throw new IllegalArgumentException("jitter must match the inequality: 0 <= jitter < ttl");
        }

        this.delegate = Objects.requireNonNull(delegate);
        this.accountCache = SettingsCache.createCache(ttl, size, jitter);
        this.accountToErrorCache = SettingsCache.createCache(ttl, size, jitter);
        this.adServerPublisherToErrorCache = SettingsCache.createCache(ttl, size, jitter);
        this.categoryConfigCache = SettingsCache.createCache(ttl, size, jitter);
        this.cache = Objects.requireNonNull(cache);
        this.ampCache = Objects.requireNonNull(ampCache);
        this.videoCache = Objects.requireNonNull(videoCache);
        this.profileCache = Objects.requireNonNull(profileCache);
        this.metrics = Objects.requireNonNull(metrics);
    }

    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return getFromCacheOrDelegate(
                accountCache,
                accountToErrorCache,
                StringUtils.isBlank(accountId) ? StringUtils.EMPTY : accountId,
                timeout,
                delegate::getAccountById,
                event -> metrics.updateSettingsCacheEventMetric(MetricName.account, event));
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

    private static <T> Future<T> cacheAndReturnFailedFuture(Throwable throwable,
                                                            String key,
                                                            Map<String, String> cache) {

        if (throwable instanceof PreBidException) {
            cache.put(key, throwable.getMessage());
        }

        return Future.failedFuture(throwable);
    }

    @Override
    public Future<StoredDataResult<String>> getStoredData(String accountId,
                                                          Set<String> requestIds,
                                                          Set<String> impIds,
                                                          Timeout timeout) {

        return getStoredDataFromCacheOrDelegate(cache, accountId, requestIds, impIds, timeout, delegate::getStoredData);
    }

    @Override
    public Future<StoredDataResult<String>> getAmpStoredData(String accountId,
                                                             Set<String> requestIds,
                                                             Set<String> impIds,
                                                             Timeout timeout) {

        return getStoredDataFromCacheOrDelegate(
                ampCache, accountId, requestIds, impIds, timeout, delegate::getAmpStoredData);
    }

    @Override
    public Future<StoredDataResult<String>> getVideoStoredData(String accountId,
                                                               Set<String> requestIds,
                                                               Set<String> impIds,
                                                               Timeout timeout) {

        return getStoredDataFromCacheOrDelegate(
                videoCache, accountId, requestIds, impIds, timeout, delegate::getVideoStoredData);
    }

    @Override
    public Future<StoredDataResult<Profile>> getProfiles(String accountId,
                                                         Set<String> requestIds,
                                                         Set<String> impIds,
                                                         Timeout timeout) {

        return getStoredDataFromCacheOrDelegate(
                profileCache, accountId, requestIds, impIds, timeout, delegate::getProfiles);
    }

    private static <T> Future<StoredDataResult<T>> getStoredDataFromCacheOrDelegate(SettingsCache<T> cache,
                                                                                    String accountId,
                                                                                    Set<String> requestIds,
                                                                                    Set<String> impIds,
                                                                                    Timeout timeout,
                                                                                    StoredDataFetcher<T> retriever) {

        // empty string account ID doesn't make sense
        final String normalizedAccountId = StringUtils.stripToNull(accountId);

        final Map<String, Set<StoredItem<T>>> requestCache = cache.getRequestCache();
        final Map<String, Set<StoredItem<T>>> impCache = cache.getImpCache();

        final Set<String> missedRequestIds = new HashSet<>();
        final Map<String, T> storedIdToRequest = getStoredDataFromCacheOrAddMissedIds(
                normalizedAccountId, requestIds, requestCache, missedRequestIds);

        final Set<String> missedImpIds = new HashSet<>();
        final Map<String, T> storedIdToImp = getStoredDataFromCacheOrAddMissedIds(
                normalizedAccountId, impIds, impCache, missedImpIds);

        if (missedRequestIds.isEmpty() && missedImpIds.isEmpty()) {
            return Future.succeededFuture(
                    StoredDataResult.of(
                            Collections.unmodifiableMap(storedIdToRequest),
                            Collections.unmodifiableMap(storedIdToImp),
                            Collections.emptyList()));
        }

        return retriever.apply(normalizedAccountId, missedRequestIds, missedImpIds, timeout).map(result -> {
            final Map<String, T> storedIdToRequestFromDelegate = result.getStoredIdToRequest();
            storedIdToRequest.putAll(storedIdToRequestFromDelegate);
            for (Map.Entry<String, T> entry : storedIdToRequestFromDelegate.entrySet()) {
                cache.saveRequestCache(normalizedAccountId, entry.getKey(), entry.getValue());
            }

            final Map<String, T> storedIdToImpFromDelegate = result.getStoredIdToImp();
            storedIdToImp.putAll(storedIdToImpFromDelegate);
            for (Map.Entry<String, T> entry : storedIdToImpFromDelegate.entrySet()) {
                cache.saveImpCache(normalizedAccountId, entry.getKey(), entry.getValue());
            }

            return StoredDataResult.of(
                    Collections.unmodifiableMap(storedIdToRequest),
                    Collections.unmodifiableMap(storedIdToImp),
                    result.getErrors());
        });
    }

    private static <T> Map<String, T> getStoredDataFromCacheOrAddMissedIds(String accountId,
                                                                           Set<String> ids,
                                                                           Map<String, Set<StoredItem<T>>> cache,
                                                                           Set<String> missedIds) {

        final Map<String, T> idToStoredItem = new HashMap<>(ids.size());

        for (String id : ids) {
            try {
                final StoredItem<T> resolvedStoredItem = StoredItemResolver.resolve(null, accountId, id, cache.get(id));
                idToStoredItem.put(id, resolvedStoredItem.getData());
            } catch (PreBidException e) {
                missedIds.add(id);
            }
        }

        return idToStoredItem;
    }

    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        return delegate.getStoredResponses(responseIds, timeout);
    }

    @Override
    public Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout) {
        final String compoundKey = StringUtils.isNotBlank(publisher)
                ? "%s_%s".formatted(primaryAdServer, publisher)
                : primaryAdServer;

        return getFromCacheOrDelegate(
                categoryConfigCache,
                adServerPublisherToErrorCache,
                compoundKey,
                timeout,
                (key, timeoutParam) -> delegate.getCategories(primaryAdServer, publisher, timeout),
                CachingApplicationSettings::noOp);
    }

    public void invalidateAccountCache(String accountId) {
        accountCache.remove(accountId);
        accountToErrorCache.remove(accountId);
        logger.debug("Account with id {} was invalidated", accountId);
    }

    private static <ANY> void noOp(ANY any) {
    }
}
