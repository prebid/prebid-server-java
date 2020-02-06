package org.prebid.server.settings;

import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.Set;

/**
 * Defines the contract of getting application settings (account, stored ad unit configurations and
 * stored requests and imps) from the source.
 *
 * @see FileApplicationSettings
 * @see JdbcApplicationSettings
 * @see HttpApplicationSettings
 * @see CachingApplicationSettings
 * @see CompositeApplicationSettings
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
     * Fetches stored requests and imps
     */
    Future<StoredDataResult> getStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout);

    /**
     * Fetches stored response
     */
    Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout);

    /**
     * Fetches AMP stored requests and imps
     */
    Future<StoredDataResult> getAmpStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout);

    /**
     * Fetches Video stored requests and imps
     */
    Future<StoredDataResult> getVideoStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout);
}
