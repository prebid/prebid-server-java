package org.prebid.server.cache.account;

import io.vertx.core.Future;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.execution.Timeout;

/**
 * Describes obtaining cache related information for account.
 */
public interface AccountCacheService {

    /**
     * Returns {@link CacheTtl} for the given account ID.
     */
    Future<CacheTtl> getCacheTtlByAccountId(String accountId, Timeout timeout);
}
