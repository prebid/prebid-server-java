package org.prebid.server.settings;

import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredRequestResult;

import java.util.Set;

/**
 * Defines the contract of getting application settings (account, stored ad unit configurations and stored requests)
 * from the source.
 *
 * @see FileApplicationSettings
 * @see JdbcApplicationSettings
 * @see CachingApplicationSettings
 */
public interface ApplicationSettings {

    /**
     * Returns {@link Account} info for given a accountId
     */
    Future<Account> getAccountById(String accountId, Timeout timeout);


    /**
     * Returns AddUnitConfig info for a given adUnitConfigId
     */
    Future<String> getAdUnitConfigById(String adUnitConfigId, Timeout timeout);

    /**
     * Fetches stored requests by ids.
     */
    Future<StoredRequestResult> getStoredRequestsById(Set<String> ids, Timeout timeout);

    /**
     * Fetches stored requests by amp ids.
     */
    Future<StoredRequestResult> getStoredRequestsByAmpId(Set<String> ids, Timeout timeout);
}
