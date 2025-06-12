package org.prebid.server.settings.helper;

import io.vertx.core.Future;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.settings.model.StoredDataResult;

import java.util.Set;

@FunctionalInterface
public interface StoredDataFetcher {

    Future<StoredDataResult> apply(String account, Set<String> reqIds, Set<String> impIds, Timeout timeout);
}
