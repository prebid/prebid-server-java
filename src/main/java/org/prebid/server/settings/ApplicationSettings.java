package org.prebid.server.settings;

import io.vertx.core.Future;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredProfileResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Defines the contract of getting application settings (account, stored ad unit configurations and
 * stored requests and imps) from the source.
 *
 * @see FileApplicationSettings
 * @see DatabaseApplicationSettings
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
     * Fetches profiles for request and imps by IDs.
     */
    // TODO: remove default
    default Future<StoredProfileResult> getProfiles(String accountId, Set<String> requestIds, Set<String> impIds,
                                                    Timeout timeout) {

        return Future.succeededFuture(StoredProfileResult.of(
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyList()));
    }

    /**
     * Fetches stored response by IDs.
     */
    Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout);


    /**
     * Fetches video category
     */
    Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout);
}
