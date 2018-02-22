package org.prebid.settings;

import io.vertx.core.Future;
import org.prebid.execution.GlobalTimeout;
import org.prebid.settings.model.Account;

public interface ApplicationSettings {

    Future<Account> getAccountById(String accountId, GlobalTimeout timeout);

    Future<String> getAdUnitConfigById(String adUnitConfigId, GlobalTimeout timeout);
}
