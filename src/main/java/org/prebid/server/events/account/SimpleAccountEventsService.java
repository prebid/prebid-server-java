package org.prebid.server.events.account;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.execution.Timeout;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeoutException;

public class SimpleAccountEventsService implements AccountEventsService {

    private static final Logger logger = LoggerFactory.getLogger(SimpleAccountEventsService.class);

    private final List<String> accountsEnabled;

    public SimpleAccountEventsService(List<String> accountsEnabled) {
        this.accountsEnabled = Objects.requireNonNull(accountsEnabled);
    }

    @Override
    public Future<Boolean> eventsEnabled(String accountId, Timeout timeout) {
        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return failResponse(accountId, new TimeoutException("Timeout has been exceeded"));
        }

        return Future.succeededFuture(accountsEnabled.contains(accountId));
    }

    private static Future<Boolean> failResponse(String accountId, Throwable exception) {
        logger.warn("Failed to fetch events Enabled for account: {0}", exception, accountId);
        return Future.failedFuture(exception);
    }
}
