package org.prebid.server.settings;

import io.vertx.core.Future;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.settings.model.Account;

/**
 * Defines the contract of getting application settings from the source.
 *
 * @see FileApplicationSettings
 * @see JdbcApplicationSettings
 * @see CachingApplicationSettings
 */
public interface ApplicationSettings {

    /**
     * Returns {@link Account} info for given a accountId
     */
    Future<Account> getAccountById(String accountId, GlobalTimeout timeout);


    /**
     * Returns AddUnitConfig info for a given adUnitConfigId
     */
    Future<String> getAdUnitConfigById(String adUnitConfigId, GlobalTimeout timeout);
}
