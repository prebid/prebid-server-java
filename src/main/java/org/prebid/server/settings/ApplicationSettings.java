package org.prebid.server.settings;

import io.vertx.core.Future;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.settings.model.Account;

public interface ApplicationSettings {

    Future<Account> getAccountById(String accountId, GlobalTimeout timeout);

    Future<String> getAdUnitConfigById(String adUnitConfigId, GlobalTimeout timeout);
}
