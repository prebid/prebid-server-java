package org.prebid.server.settings;

import io.vertx.core.Future;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.settings.model.Account;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

public class CachingApplicationSettings implements ApplicationSettings {

    private final ApplicationSettings delegate;
    private final Map<String, Account> accountCache;
    private final Map<String, String> adUnitConfigCache;

    public CachingApplicationSettings(ApplicationSettings delegate, int ttl, int size) {
        if (ttl <= 0 || size <= 0) {
            throw new IllegalArgumentException("ttl and size must be positive");
        }
        this.delegate = Objects.requireNonNull(delegate);
        // another option is to use caffeine
        this.accountCache = new PassiveExpiringMap<>(ttl, TimeUnit.SECONDS, new LRUMap<>(size));
        this.adUnitConfigCache = new PassiveExpiringMap<>(ttl, TimeUnit.SECONDS, new LRUMap<>(size));
    }

    @Override
    public Future<Account> getAccountById(String accountId, GlobalTimeout timeout) {
        return getFromCacheOrDelegate(accountCache, accountId, timeout, delegate::getAccountById);
    }

    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId, GlobalTimeout timeout) {
        return getFromCacheOrDelegate(adUnitConfigCache, adUnitConfigId, timeout, delegate::getAdUnitConfigById);
    }

    private static <T> Future<T> getFromCacheOrDelegate(Map<String, T> cache, String key, GlobalTimeout timeout,
                                                        BiFunction<String, GlobalTimeout, Future<T>> retriever) {
        Objects.requireNonNull(key);
        Objects.requireNonNull(timeout);

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
}
