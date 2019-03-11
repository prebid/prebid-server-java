package org.prebid.server.cache.account;

import io.vertx.core.Future;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.execution.Timeout;

import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public class CompositeAccountCacheService implements AccountCacheService {

    private final Proxy proxy;

    public CompositeAccountCacheService(List<AccountCacheService> delegates) {
        if (Objects.requireNonNull(delegates).isEmpty()) {
            throw new IllegalArgumentException("At least one application settings implementation required");
        }
        proxy = createProxy(delegates);
    }

    private static Proxy createProxy(List<AccountCacheService> delegates) {
        Proxy proxy = null;

        final ListIterator<AccountCacheService> iterator = delegates.listIterator(delegates.size());
        while (iterator.hasPrevious()) {
            proxy = new Proxy(iterator.previous(), proxy);
        }

        return proxy;
    }

    @Override
    public Future<CacheTtl> getCacheTtlByAccountId(String accountId, Timeout timeout) {
        return proxy.getCacheTtlByAccountId(accountId, timeout);
    }

    private static class Proxy implements AccountCacheService {

        private AccountCacheService accountCacheService;
        private Proxy next;

        private Proxy(AccountCacheService accountCacheService, Proxy next) {
            this.accountCacheService = accountCacheService;
            this.next = next;
        }

        @Override
        public Future<CacheTtl> getCacheTtlByAccountId(String accountId, Timeout timeout) {
            return accountCacheService.getCacheTtlByAccountId(accountId, timeout)
                    .compose(cacheTtl -> isCacheTtlEmpty(cacheTtl) && next != null
                            ? next.getCacheTtlByAccountId(accountId, timeout)
                            : Future.succeededFuture(cacheTtl))
                    .recover(throwable -> next != null
                            ? next.getCacheTtlByAccountId(accountId, timeout)
                            : Future.succeededFuture(CacheTtl.empty()));
        }

        private static boolean isCacheTtlEmpty(CacheTtl cacheTtl) {
            return cacheTtl == null || cacheTtl.getBannerCacheTtl() == null && cacheTtl.getVideoCacheTtl() == null;
        }
    }
}
