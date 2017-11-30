package org.rtb.vexing.settings;

import io.vertx.core.Future;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.collections4.map.PassiveExpiringMap;
import org.rtb.vexing.settings.model.Account;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

class CachingApplicationSettings implements ApplicationSettings {

    private final ApplicationSettings delegate;
    private final Map<String, Account> accountCache;
    private final Map<String, String> adUnitConfigCache;

    CachingApplicationSettings(ApplicationSettings delegate, int ttl, int size) {
        if (ttl <= 0 || size <= 0) {
            throw new IllegalArgumentException("ttl and size must be positive");
        }
        this.delegate = Objects.requireNonNull(delegate);
        // another option is to use caffeine
        this.accountCache = new PassiveExpiringMap<>(ttl, TimeUnit.SECONDS, new LRUMap<>(size));
        this.adUnitConfigCache = new PassiveExpiringMap<>(ttl, TimeUnit.SECONDS, new LRUMap<>(size));
    }

    @Override
    public Future<Account> getAccountById(String accountId) {
        return getFromCacheOrDelegate(accountCache, accountId, delegate::getAccountById);
    }

    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId) {
        return getFromCacheOrDelegate(adUnitConfigCache, adUnitConfigId, delegate::getAdUnitConfigById);
    }

    private static <T> Future<T> getFromCacheOrDelegate(Map<String, T> cache, String key,
                                                        Function<String, Future<T>> retriever) {
        Objects.requireNonNull(key);

        final Future<T> result;

        final T cachedValue = cache.get(key);
        if (cachedValue != null) {
            result = Future.succeededFuture(cachedValue);
        } else {
            result = retriever.apply(key)
                    .map(value -> {
                        cache.put(key, value);
                        return value;
                    });
        }

        return result;
    }
}
