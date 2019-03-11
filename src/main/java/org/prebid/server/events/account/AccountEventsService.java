package org.prebid.server.events.account;

import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;

public interface AccountEventsService {

    Future<Boolean> eventsEnabled(String accountId, Timeout timeout);
}
