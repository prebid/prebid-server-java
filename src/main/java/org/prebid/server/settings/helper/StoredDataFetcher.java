package org.prebid.server.settings.helper;

import io.vertx.core.Future;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.settings.model.StoredDataResult;

import java.util.Set;

@FunctionalInterface
public interface StoredDataFetcher<T> {

    Future<StoredDataResult<T>> apply(String account, Set<String> reqIds, Set<String> impIds, Timeout timeout);
}
