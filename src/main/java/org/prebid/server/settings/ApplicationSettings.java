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
     * Returns {@link Account} for the given account ID.
     */
    Future<Account> getAccountById(String accountId, Timeout timeout);

    /**
     * Returns configuration for the given adUnit config ID.
     */
    Future<String> getAdUnitConfigById(String adUnitConfigId, Timeout timeout);

    /**
     * Fetches stored requests and imps by IDs.
     */
    Future<StoredDataResult> getStoredData(String accountId, Set<String> requestIds, Set<String> impIds,
                                           Timeout timeout);

    /**
     * Fetches AMP stored requests and imps by IDs.
     */
    Future<StoredDataResult> getAmpStoredData(String accountId, Set<String> requestIds, Set<String> impIds,
                                              Timeout timeout);

    /**
     * Fetches Video stored requests and imps by IDs.
     */
    Future<StoredDataResult> getVideoStoredData(String accountId, Set<String> requestIds, Set<String> impIds,
                                                Timeout timeout);

    /**
     * Fetches stored response by IDs.
     */
    Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout);
}
