package org.rtb.vexing.settings;

import io.vertx.core.Future;
import org.rtb.vexing.settings.model.Account;

public interface ApplicationSettings {

    Future<Account> getAccountById(String accountId);

    Future<String> getAdUnitConfigById(String adUnitConfigId);
}
