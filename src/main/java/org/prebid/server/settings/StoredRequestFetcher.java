package org.prebid.server.settings;

import io.vertx.core.Future;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.settings.model.StoredRequestResult;

import java.util.Set;

/**
 * Interface for executing stored requests fetching from sources.
 */
public interface StoredRequestFetcher {

    /**
     * Fetches stored requests by ids.
     */
    Future<StoredRequestResult> getStoredRequestsById(Set<String> ids, GlobalTimeout timeout);

    /**
     * Fetches stored requests by amp ids.
     */
    Future<StoredRequestResult> getStoredRequestsByAmpId(Set<String> ids, GlobalTimeout timeout);
}
