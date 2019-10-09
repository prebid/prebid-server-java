package org.prebid.server.settings.model;

/**
 * Interface to satisfy obtaining {@link StoredDataResult}
 *
 * @param <ACC>  account ID
 * @param <REQS> set of stored request IDs
 * @param <IMPS> set of stored imp IDs
 * @param <T>    processing timeout
 * @param <R>    result of fetching stored data
 */
// FIXME: rename (or remove???) after AMP account ID will be added
@FunctionalInterface
public interface TriFunction<ACC, REQS, IMPS, T, R> {
    R apply(ACC account, REQS reqIds, IMPS impIds, T timeout);
}
