package org.rtb.vexing.settings;

import io.vertx.core.Future;
import org.rtb.vexing.execution.GlobalTimeout;
import org.rtb.vexing.settings.model.StoredRequestResult;

import java.util.Set;

/**
 * Interface for executing stored requests fetching from sources.
 */
public interface StoredRequestFetcher {

    /**
     * Fetches stored requests by ids.
     */
    Future<StoredRequestResult> getStoredRequestsById(Set<String> ids, GlobalTimeout timeout);
}
