package org.prebid.server.settings;

import io.vertx.core.Future;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredRequestResult;

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

    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return proxy.getAccountById(accountId, timeout);
    }

    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId, Timeout timeout) {
        return proxy.getAdUnitConfigById(adUnitConfigId, timeout);
    }

    @Override
    public Future<StoredRequestResult> getStoredRequestsById(Set<String> ids, Timeout timeout) {
        return proxy.getStoredRequestsById(ids, timeout);
    }

    @Override
    public Future<StoredRequestResult> getStoredRequestsByAmpId(Set<String> ids, Timeout timeout) {
        return proxy.getStoredRequestsByAmpId(ids, timeout);
    }

    private static class Proxy implements ApplicationSettings {

        private ApplicationSettings applicationSettings;
        private Proxy next;

        Proxy(ApplicationSettings applicationSettings, Proxy next) {
            this.applicationSettings = applicationSettings;
            this.next = next;
        }

        @Override
        public Future<Account> getAccountById(String accountId, Timeout timeout) {
            final BiFunction<String, Timeout, Future<Account>> nextRetriever =
                    next != null ? next::getAccountById : null;
            return getConfig(accountId, timeout, applicationSettings::getAccountById, nextRetriever);
        }

        @Override
        public Future<String> getAdUnitConfigById(String adUnitConfigId, Timeout timeout) {
            final BiFunction<String, Timeout, Future<String>> nextRetriever =
                    next != null ? next::getAdUnitConfigById : null;
            return getConfig(adUnitConfigId, timeout, applicationSettings::getAdUnitConfigById, nextRetriever);
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
        public Future<StoredRequestResult> getStoredRequestsById(Set<String> ids, Timeout timeout) {
            final BiFunction<Set<String>, Timeout, Future<StoredRequestResult>> nextRetriever =
                    next != null ? next::getStoredRequestsById : null;
            return getStoredRequests(ids, timeout, applicationSettings::getStoredRequestsById, nextRetriever);
        }

        @Override
        public Future<StoredRequestResult> getStoredRequestsByAmpId(Set<String> ids, Timeout timeout) {
            final BiFunction<Set<String>, Timeout, Future<StoredRequestResult>> nextRetriever =
                    next != null ? next::getStoredRequestsByAmpId : null;
            return getStoredRequests(ids, timeout, applicationSettings::getStoredRequestsByAmpId, nextRetriever);
        }

        private static Future<StoredRequestResult> getStoredRequests(
                Set<String> ids, Timeout timeout,
                BiFunction<Set<String>, Timeout, Future<StoredRequestResult>> retriever,
                BiFunction<Set<String>, Timeout, Future<StoredRequestResult>> nextRetriever) {

            return retriever.apply(ids, timeout)
                    .compose(retrieverResult ->
                            nextRetriever == null || retrieverResult.getStoredIdToJson().size() == ids.size()
                                    ? Future.succeededFuture(retrieverResult)
                                    : getRemainingStoredRequests(ids, timeout, retrieverResult.getStoredIdToJson(),
                                    nextRetriever));
        }

        private static Future<StoredRequestResult> getRemainingStoredRequests(
                Set<String> ids, Timeout timeout, Map<String, String> fetchedStoredIdToJson,
                BiFunction<Set<String>, Timeout, Future<StoredRequestResult>> retriever) {

            return retriever.apply(subtractSets(ids, fetchedStoredIdToJson.keySet()), timeout)
                    .compose(result -> Future.succeededFuture(StoredRequestResult.of(
                            combineMaps(fetchedStoredIdToJson, result.getStoredIdToJson()), result.getErrors())));
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
