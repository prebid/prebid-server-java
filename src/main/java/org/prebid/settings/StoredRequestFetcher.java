package org.prebid.settings;

import io.vertx.core.Future;
import org.prebid.execution.GlobalTimeout;
import org.prebid.settings.model.StoredRequestResult;

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
