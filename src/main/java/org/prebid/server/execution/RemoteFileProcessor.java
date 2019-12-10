package org.prebid.server.execution;

import io.vertx.core.Future;

/**
 * Contract fro services which use external files.
 */
public interface RemoteFileProcessor {

    Future<?> setDataPath(String dataFilePath);
}

