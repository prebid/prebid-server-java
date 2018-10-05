package org.prebid.server.cache.account;

import io.vertx.core.Future;
import org.prebid.server.cache.model.CacheTtl;

import java.util.Map;
import java.util.Objects;

/**
 * Simple implementation of {@link AccountCacheService} based on {@link Map}
 * where key is the account ID and value is {@link CacheTtl}.
 */
public class SimpleAccountCacheService implements AccountCacheService {

    private final Map<String, CacheTtl> accountToCacheTtl;

    public SimpleAccountCacheService(Map<String, CacheTtl> accountToCacheTtl) {
        this.accountToCacheTtl = Objects.requireNonNull(accountToCacheTtl);
    }

    @Override
    public Future<CacheTtl> getCacheTtlByAccountId(String accountId) {
        return Future.succeededFuture(accountToCacheTtl.getOrDefault(accountId, CacheTtl.empty()));
    }
}
