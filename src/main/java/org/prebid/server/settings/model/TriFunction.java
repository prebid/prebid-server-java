package org.prebid.server.settings.model;

/**
 * Interface to satisfy obtaining {@link StoredDataResult}
 *
 * @param <T> set of stored request IDs
 * @param <U> set of stored imp IDs
 * @param <V> processing timeout
 * @param <R> result of fetching stored data
 */
@FunctionalInterface
public interface TriFunction<T, U, V, R> {

    R apply(T t, U u, V v);
}
