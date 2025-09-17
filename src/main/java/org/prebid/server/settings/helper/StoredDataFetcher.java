package org.prebid.server.settings.helper;

<<<<<<< HEAD
import io.vertx.core.Future;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.settings.model.StoredDataResult;

import java.util.Set;

@FunctionalInterface
public interface StoredDataFetcher<T> {

    Future<StoredDataResult<T>> apply(String account, Set<String> reqIds, Set<String> impIds, Timeout timeout);
=======
import org.prebid.server.settings.model.StoredDataResult;

/**
 * Interface to satisfy obtaining of {@link StoredDataResult}.
 *
 * @param <ACC>  account ID
 * @param <REQS> set of stored request IDs
 * @param <IMPS> set of stored imp IDs
 * @param <T>    processing timeout
 * @param <R>    result of fetching stored data
 */
@FunctionalInterface
public interface StoredDataFetcher<ACC, REQS, IMPS, T, R> {

    R apply(ACC account, REQS reqIds, IMPS impIds, T timeout);
>>>>>>> 04d9d4a13 (Initial commit)
}
