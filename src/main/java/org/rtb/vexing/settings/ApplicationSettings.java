package org.rtb.vexing.settings;

import io.vertx.core.Future;
import org.rtb.vexing.execution.GlobalTimeout;
import org.rtb.vexing.settings.model.Account;

public interface ApplicationSettings {

    Future<Account> getAccountById(String accountId, GlobalTimeout timeout);

    Future<String> getAdUnitConfigById(String adUnitConfigId, GlobalTimeout timeout);
}
