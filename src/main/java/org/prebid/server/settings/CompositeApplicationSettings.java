package org.prebid.server.settings;

import io.vertx.core.Future;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.settings.helper.StoredDataFetcher;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.Profile;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class CompositeApplicationSettings implements ApplicationSettings {

    private final Proxy proxy;

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
    public Future<StoredDataResult<String>> getStoredData(String accountId,
                                                          Set<String> requestIds,
                                                          Set<String> impIds,
                                                          Timeout timeout) {

        return proxy.getStoredData(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredDataResult<String>> getAmpStoredData(String accountId,
                                                             Set<String> requestIds,
                                                             Set<String> impIds,
                                                             Timeout timeout) {

        return proxy.getAmpStoredData(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredDataResult<String>> getVideoStoredData(String accountId,
                                                               Set<String> requestIds,
                                                               Set<String> impIds,
                                                               Timeout timeout) {

        return proxy.getVideoStoredData(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredDataResult<Profile>> getProfiles(String accountId,
                                                         Set<String> requestIds,
                                                         Set<String> impIds,
                                                         Timeout timeout) {

        return proxy.getProfiles(accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        return proxy.getStoredResponses(responseIds, timeout);
    }

    @Override
    public Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout) {
        return proxy.getCategories(primaryAdServer, publisher, timeout);
    }

    private static class Proxy implements ApplicationSettings {

        private final ApplicationSettings applicationSettings;
        private final Proxy next;

        private Proxy(ApplicationSettings applicationSettings, Proxy next) {
            this.applicationSettings = applicationSettings;
            this.next = next;
        }

        @Override
        public Future<Account> getAccountById(String accountId, Timeout timeout) {
            return applicationSettings.getAccountById(accountId, timeout)
                    .recover(throwable -> next != null
                            ? next.getAccountById(accountId, timeout)
                            : Future.failedFuture(throwable));
        }

        @Override
        public Future<StoredDataResult<String>> getStoredData(String accountId,
                                                              Set<String> requestIds,
                                                              Set<String> impIds,
                                                              Timeout timeout) {

            return getStoredDataOrDelegate(
                    accountId,
                    requestIds,
                    impIds,
                    timeout,
                    applicationSettings::getStoredData,
                    next != null ? next::getStoredData : null);
        }

        @Override
        public Future<StoredDataResult<String>> getAmpStoredData(String accountId,
                                                                 Set<String> requestIds,
                                                                 Set<String> impIds,
                                                                 Timeout timeout) {

            return getStoredDataOrDelegate(
                    accountId,
                    requestIds,
                    impIds,
                    timeout,
                    applicationSettings::getAmpStoredData,
                    next != null ? next::getAmpStoredData : null);
        }

        @Override
        public Future<StoredDataResult<String>> getVideoStoredData(String accountId,
                                                                   Set<String> requestIds,
                                                                   Set<String> impIds,
                                                                   Timeout timeout) {

            return getStoredDataOrDelegate(
                    accountId,
                    requestIds,
                    impIds,
                    timeout,
                    applicationSettings::getVideoStoredData,
                    next != null ? next::getVideoStoredData : null);
        }

        @Override
        public Future<StoredDataResult<Profile>> getProfiles(String accountId,
                                                             Set<String> requestIds,
                                                             Set<String> impIds,
                                                             Timeout timeout) {

            return getStoredDataOrDelegate(
                    accountId,
                    requestIds,
                    impIds,
                    timeout,
                    applicationSettings::getProfiles,
                    next != null ? next::getProfiles : null);
        }

        private static <T> Future<StoredDataResult<T>> getStoredDataOrDelegate(String accountId,
                                                                               Set<String> requestIds,
                                                                               Set<String> impIds,
                                                                               Timeout timeout,
                                                                               StoredDataFetcher<T> retriever,
                                                                               StoredDataFetcher<T> nextRetriever) {

            return retriever.apply(accountId, requestIds, impIds, timeout)
                    .compose(retrieverResult -> nextRetriever == null || retrieverResult.getErrors().isEmpty()
                            ? Future.succeededFuture(retrieverResult)
                            : getRemainingStoredData(
                            accountId,
                            requestIds,
                            impIds,
                            timeout,
                            retrieverResult.getStoredIdToRequest(),
                            retrieverResult.getStoredIdToImp(),
                            nextRetriever));
        }

        private static <T> Future<StoredDataResult<T>> getRemainingStoredData(String accountId,
                                                                              Set<String> requestIds,
                                                                              Set<String> impIds,
                                                                              Timeout timeout,
                                                                              Map<String, T> storedIdToRequest,
                                                                              Map<String, T> storedIdToImp,
                                                                              StoredDataFetcher<T> retriever) {

            return retriever.apply(
                            accountId,
                            subtractSets(requestIds, storedIdToRequest.keySet()),
                            subtractSets(impIds, storedIdToImp.keySet()),
                            timeout)
                    .map(result -> StoredDataResult.of(
                            combineMaps(storedIdToRequest, result.getStoredIdToRequest()),
                            combineMaps(storedIdToImp, result.getStoredIdToImp()),
                            result.getErrors()));
        }


        @Override
        public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
            return applicationSettings.getStoredResponses(responseIds, timeout)
                    .compose(result -> next == null || result.getErrors().isEmpty()
                            ? Future.succeededFuture(result)
                            : getRemainingStoredResponses(responseIds, timeout, result.getIdToStoredResponses()));
        }

        private Future<StoredResponseDataResult> getRemainingStoredResponses(
                Set<String> responseIds,
                Timeout timeout,
                Map<String, String> storedSeatBids) {

            return next.getStoredResponses(subtractSets(responseIds, storedSeatBids.keySet()), timeout)
                    .map(result -> StoredResponseDataResult.of(
                            combineMaps(storedSeatBids, result.getIdToStoredResponses()),
                            result.getErrors()));
        }

        @Override
        public Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout) {
            return applicationSettings.getCategories(primaryAdServer, publisher, timeout)
                    .recover(throwable -> next != null
                            ? next.getCategories(primaryAdServer, publisher, timeout)
                            : Future.failedFuture(throwable));
        }

        private static <T> Set<T> subtractSets(Set<T> first, Set<T> second) {
            final Set<T> remaining = new HashSet<>(first);
            remaining.removeAll(second);
            return remaining;
        }

        private static <K, V> Map<K, V> combineMaps(Map<K, V> first, Map<K, V> second) {
            final Map<K, V> combined = new HashMap<>(first.size() + second.size());
            combined.putAll(first);
            combined.putAll(second);
            return combined;
        }
    }
}
