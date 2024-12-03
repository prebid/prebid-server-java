package org.prebid.server.execution.file;

import io.vertx.core.Future;

public interface FileProcessor {

    Future<?> setDataPath(String dataFilePath);
}
