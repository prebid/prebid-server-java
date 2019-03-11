package org.prebid.server.events.account;

import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.ApplicationSettings;

import java.util.Objects;

public class SettingsAccountEventsService implements AccountEventsService {

    final ApplicationSettings applicationSettings;

    public SettingsAccountEventsService(ApplicationSettings applicationSettings) {
        this.applicationSettings = Objects.requireNonNull(applicationSettings);
    }

    @Override
    public Future<Boolean> eventsEnabled(String accountId, Timeout timeout) {
        return applicationSettings.getOrtb2AccountById(accountId, timeout)
                .compose(account -> Future.succeededFuture(account == null ? null : account.getEventsRequired()));
    }
}
