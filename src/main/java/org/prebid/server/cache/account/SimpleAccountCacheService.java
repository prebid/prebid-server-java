package org.prebid.server.cache.account;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.cache.model.CacheTtl;
import org.prebid.server.execution.Timeout;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

/**
 * Simple implementation of {@link AccountCacheService} based on {@link Map},
 * where key is an account ID and value is a {@link CacheTtl}.
 */
public class SimpleAccountCacheService implements AccountCacheService {

    private static final Logger logger = LoggerFactory.getLogger(SimpleAccountCacheService.class);

    private final Map<String, CacheTtl> accountToCacheTtl;

    public SimpleAccountCacheService(Map<String, CacheTtl> accountToCacheTtl) {
        this.accountToCacheTtl = Objects.requireNonNull(accountToCacheTtl);
    }

    @Override
    public Future<CacheTtl> getCacheTtlByAccountId(String accountId, Timeout timeout) {
        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return failResponse(accountId, new TimeoutException("Timeout has been exceeded"));
        }

        return Future.succeededFuture(accountToCacheTtl.getOrDefault(accountId, CacheTtl.empty()));
    }

    /**
     * Handles errors occurred while request or response processing.
     */
    private static Future<CacheTtl> failResponse(String accountId, Throwable exception) {
        logger.warn("Failed to fetch cache ttl for account: {0}", exception, accountId);
        return Future.failedFuture(exception);
    }
}
