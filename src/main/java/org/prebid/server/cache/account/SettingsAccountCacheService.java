package org.prebid.server.cache.account;

import io.vertx.core.Future;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.ApplicationSettings;

public class SettingsAccountCacheService implements AccountCacheService {

    private final ApplicationSettings applicationSettings;

    public SettingsAccountCacheService(ApplicationSettings applicationSettings) {
        this.applicationSettings = applicationSettings;
    }

    @Override
    public Future<CacheTtl> getCacheTtlByAccountId(String accountId, Timeout timeout) {
        return applicationSettings.getOrtb2AccountById(accountId, timeout)
                .compose(account -> Future.succeededFuture(account != null
                        ? CacheTtl.of(account.getBannerCacheTtl(), account.getVideoCacheTtl())
                        : CacheTtl.empty()));
    }
}
