package org.prebid.server.settings;

import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;
import org.prebid.server.settings.model.TriFunction;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Implements composite pattern for a list of {@link ApplicationSettings}.
 */
public class CompositeApplicationSettings implements ApplicationSettings {

    private Proxy proxy;

    public CompositeApplicationSettings(List<ApplicationSettings> delegates) {
        if (Objects.requireNonNull(delegates).isEmpty()) {
            throw new IllegalArgumentException("At least one application settings implementation required");
        }
        proxy = createProxy(delegates);
    }

    private static Proxy createProxy(List<ApplicationSettings> delegates) {
        Proxy proxy = null;

        final ListIterator<ApplicationSettings> iterator = delegates.listIterator(delegates.size());
        while (iterator.hasPrevious()) {
            proxy = new Proxy(iterator.previous(), proxy);
        }

        return proxy;
    }

    /**
     * Runs a process to get account by id from a chain of retrievers
     * and returns {@link Future&lt;{@link Account}&gt;}
     */
    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return proxy.getAccountById(accountId, timeout);
    }

    /**
     * Runs a process to get AdUnit config by id from a chain of retrievers
     * and returns {@link Future&lt;{@link String}&gt;}
     */
    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId, Timeout timeout) {
        return proxy.getAdUnitConfigById(adUnitConfigId, timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of ids from a chain of retrievers
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}
     */
    @Override
    public Future<StoredDataResult> getStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return proxy.getStoredData(requestIds, impIds, timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of amp ids from a chain of retrievers
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}
     */
    @Override
    public Future<StoredDataResult> getAmpStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return proxy.getAmpStoredData(requestIds, Collections.emptySet(), timeout);
    }

    @Override
    public Future<StoredDataResult> getVideoStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return proxy.getVideoStoredData(requestIds, impIds, timeout);
    }

    /**
     * Runs a process to get stored responses by a collection of ids from a chain of retrievers
     * and returns {@link Future&lt;{@link StoredResponseDataResult }&gt;}
     */
    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        return proxy.getStoredResponses(responseIds, timeout);
    }

    /**
     * Decorates {@link ApplicationSettings} for a chain of retrievers
     */
    private static class Proxy implements ApplicationSettings {

        private ApplicationSettings applicationSettings;
        private Proxy next;

        private Proxy(ApplicationSettings applicationSettings, Proxy next) {
            this.applicationSettings = applicationSettings;
            this.next = next;
        }

        @Override
        public Future<Account> getAccountById(String accountId, Timeout timeout) {
            return getConfig(accountId, timeout, applicationSettings::getAccountById,
                    next != null ? next::getAccountById : null);
        }

        @Override
        public Future<String> getAdUnitConfigById(String adUnitConfigId, Timeout timeout) {
            return getConfig(adUnitConfigId, timeout, applicationSettings::getAdUnitConfigById,
                    next != null ? next::getAdUnitConfigById : null);
        }

        private static <T> Future<T> getConfig(String key, Timeout timeout,
                                               BiFunction<String, Timeout, Future<T>> retriever,
                                               BiFunction<String, Timeout, Future<T>> nextRetriever) {
            return retriever.apply(key, timeout)
                    .recover(throwable -> nextRetriever != null
                            ? nextRetriever.apply(key, timeout)
                            : Future.failedFuture(throwable));
        }

        @Override
        public Future<StoredDataResult> getStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
            return getStoredRequests(requestIds, impIds, timeout, applicationSettings::getStoredData,
                    next != null ? next::getStoredData : null);
        }

        @Override
        public Future<StoredDataResult> getAmpStoredData(Set<String> requestIds, Set<String> impIds,
                                                         Timeout timeout) {
            return getStoredRequests(requestIds, Collections.emptySet(), timeout, applicationSettings::getAmpStoredData,
                    next != null ? next::getAmpStoredData : null);
        }

        @Override
        public Future<StoredDataResult> getVideoStoredData(Set<String> requestIds, Set<String> impIds,
                                                           Timeout timeout) {
            return getStoredRequests(requestIds, impIds, timeout,
                    applicationSettings::getVideoStoredData, next != null ? next::getVideoStoredData : null);
        }

        @Override
        public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
            return getStoredResponses(responseIds, timeout, applicationSettings::getStoredResponses,
                    next != null ? next::getStoredResponses : null);
        }

        private static Future<StoredResponseDataResult> getStoredResponses(
                Set<String> responseIds, Timeout timeout,
                BiFunction<Set<String>, Timeout, Future<StoredResponseDataResult>> retriever,
                BiFunction<Set<String>, Timeout, Future<StoredResponseDataResult>> nextRetriever) {
            return retriever.apply(responseIds, timeout)
                    .compose(retrieverResult ->
                            nextRetriever == null || retrieverResult.getErrors().isEmpty()
                                    ? Future.succeededFuture(retrieverResult)
                                    : getRemainingStoredResponses(responseIds, timeout,
                                    retrieverResult.getStoredSeatBid(), nextRetriever));
        }

        private static Future<StoredDataResult> getStoredRequests(
                Set<String> requestIds, Set<String> impIds, Timeout timeout,
                TriFunction<Set<String>, Set<String>, Timeout, Future<StoredDataResult>> retriever,
                TriFunction<Set<String>, Set<String>, Timeout, Future<StoredDataResult>> nextRetriever) {

            return retriever.apply(requestIds, impIds, timeout)
                    .compose(retrieverResult ->
                            nextRetriever == null || retrieverResult.getErrors().isEmpty()
                                    ? Future.succeededFuture(retrieverResult)
                                    : getRemainingStoredRequests(requestIds, impIds, timeout,
                                    retrieverResult.getStoredIdToRequest(), retrieverResult.getStoredIdToImp(),
                                    nextRetriever));
        }

        private static Future<StoredDataResult> getRemainingStoredRequests(
                Set<String> requestIds, Set<String> impIds, Timeout timeout,
                Map<String, String> storedIdToRequest, Map<String, String> storedIdToImp,
                TriFunction<Set<String>, Set<String>, Timeout, Future<StoredDataResult>> retriever) {

            return retriever.apply(
                    subtractSets(requestIds, storedIdToRequest.keySet()),
                    subtractSets(impIds, storedIdToImp.keySet()), timeout)
                    .map(result -> StoredDataResult.of(
                            combineMaps(storedIdToRequest, result.getStoredIdToRequest()),
                            combineMaps(storedIdToImp, result.getStoredIdToImp()),
                            result.getErrors()));
        }

        private static Future<StoredResponseDataResult> getRemainingStoredResponses(
                Set<String> responseIds, Timeout timeout, Map<String, String> storedSeatBids,
                BiFunction<Set<String>, Timeout, Future<StoredResponseDataResult>> retriever) {
            return retriever.apply(subtractSets(responseIds, storedSeatBids.keySet()), timeout)
                    .map(result -> StoredResponseDataResult.of(
                            combineMaps(storedSeatBids, result.getStoredSeatBid()),
                            result.getErrors()));
        }

        private static <T> Set<T> subtractSets(Set<T> set1, Set<T> set2) {
            final Set<T> remaining = new HashSet<>(set1);
            remaining.removeAll(set2);
            return remaining;
        }

        private static <K, V> Map<K, V> combineMaps(Map<K, V> map1, Map<K, V> map2) {
            final Map<K, V> combined = new HashMap<>(map1.size() + map2.size());
            combined.putAll(map1);
            combined.putAll(map2);
            return combined;
        }
    }
}
