package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.timeout.Timeout;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
<<<<<<< HEAD
import org.prebid.server.settings.helper.DatabaseProfilesResultMapper;
=======
>>>>>>> 04d9d4a13 (Initial commit)
import org.prebid.server.settings.helper.DatabaseStoredDataResultMapper;
import org.prebid.server.settings.helper.DatabaseStoredResponseResultMapper;
import org.prebid.server.settings.helper.ParametrizedQueryHelper;
import org.prebid.server.settings.model.Account;
<<<<<<< HEAD
import org.prebid.server.settings.model.Profile;
=======
>>>>>>> 04d9d4a13 (Initial commit)
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;
import org.prebid.server.util.ObjectUtil;
import org.prebid.server.vertx.database.CircuitBreakerSecuredDatabaseClient;
import org.prebid.server.vertx.database.DatabaseClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

<<<<<<< HEAD
=======
/**
 * Implementation of {@link ApplicationSettings}.
 * <p>
 * Reads an application settings from the database source.
 * <p>
 * In order to enable caching and reduce latency for read operations {@link DatabaseApplicationSettings}
 * can be decorated by {@link CachingApplicationSettings}.
 */
>>>>>>> 04d9d4a13 (Initial commit)
public class DatabaseApplicationSettings implements ApplicationSettings {

    private final DatabaseClient databaseClient;
    private final JacksonMapper mapper;
    private final ParametrizedQueryHelper parametrizedQueryHelper;

    /**
     * Query to select account by ids.
     */
    private final String selectAccountQuery;

    /**
     * Query to select stored requests and imps by ids, for example:
     * <pre>
     * SELECT accountId, reqid, requestData, 'request' as dataType
     *   FROM stored_requests
     *   WHERE reqid in (%REQUEST_ID_LIST%)
     * UNION ALL
     * SELECT accountId, impid, impData, 'imp' as dataType
     *   FROM stored_imps
     *   WHERE impid in (%IMP_ID_LIST%)
     * </pre>
     */
    private final String selectStoredRequestsQuery;

    /**
     * Query to select amp stored requests by ids, for example:
     * <pre>
     * SELECT accountId, reqid, requestData, 'request' as dataType
     *   FROM stored_requests
     *   WHERE reqid in (%REQUEST_ID_LIST%)
     * </pre>
     */
    private final String selectAmpStoredRequestsQuery;

    /**
<<<<<<< HEAD
     * Query to select profiles by ids, for example:
     * <pre>
     * SELECT accountId, profileId, profile, mergePrecedence, type
     *   FROM profiles
     *   WHERE profileId in (%REQUEST_ID_LIST%, %IMP_ID_LIST%)
     * </pre>
     */
    private final String selectProfilesQuery;

    /**
=======
>>>>>>> 04d9d4a13 (Initial commit)
     * Query to select stored responses by ids, for example:
     * <pre>
     * SELECT respid, responseData
     *   FROM stored_responses
     *   WHERE respid in (%RESPONSE_ID_LIST%)
     * </pre>
     */
    private final String selectStoredResponsesQuery;

    public DatabaseApplicationSettings(DatabaseClient databaseClient,
                                       JacksonMapper mapper,
                                       ParametrizedQueryHelper parametrizedQueryHelper,
                                       String selectAccountQuery,
                                       String selectStoredRequestsQuery,
                                       String selectAmpStoredRequestsQuery,
<<<<<<< HEAD
                                       String selectProfilesQuery,
=======
>>>>>>> 04d9d4a13 (Initial commit)
                                       String selectStoredResponsesQuery) {

        this.databaseClient = Objects.requireNonNull(databaseClient);
        this.mapper = Objects.requireNonNull(mapper);
        this.parametrizedQueryHelper = Objects.requireNonNull(parametrizedQueryHelper);
        this.selectAccountQuery = parametrizedQueryHelper.replaceAccountIdPlaceholder(
                Objects.requireNonNull(selectAccountQuery));
        this.selectStoredRequestsQuery = Objects.requireNonNull(selectStoredRequestsQuery);
        this.selectAmpStoredRequestsQuery = Objects.requireNonNull(selectAmpStoredRequestsQuery);
<<<<<<< HEAD
        this.selectProfilesQuery = selectProfilesQuery;
        this.selectStoredResponsesQuery = Objects.requireNonNull(selectStoredResponsesQuery);
    }

=======
        this.selectStoredResponsesQuery = Objects.requireNonNull(selectStoredResponsesQuery);
    }

    /**
     * Runs a process to get account by id from database
     * and returns {@link Future}&lt;{@link Account}&gt;.
     */
>>>>>>> 04d9d4a13 (Initial commit)
    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return databaseClient.executeQuery(
                        selectAccountQuery,
                        Collections.singletonList(accountId),
                        result -> mapToModelOrError(result, this::toAccount),
                        timeout)
<<<<<<< HEAD
                .compose(result -> result != null
                        ? Future.succeededFuture(result)
                        : Future.failedFuture(new PreBidException("Account not found: " + accountId)));
    }

    /**
=======
                .compose(result -> failedIfNull(result, accountId, "Account"));
    }

    @Override
    public Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout) {
        return Future.failedFuture(new PreBidException("Not supported"));
    }

    /**
     * Transforms the first row of {@link RowSet<Row>} to required object or returns null.
     * <p>
>>>>>>> 04d9d4a13 (Initial commit)
     * Note: mapper should never throw exception in case of using
     * {@link CircuitBreakerSecuredDatabaseClient}.
     */
    private <T> T mapToModelOrError(RowSet<Row> rowSet, Function<Row, T> mapper) {
        final RowIterator<Row> rowIterator = rowSet != null ? rowSet.iterator() : null;
        return rowIterator != null && rowIterator.hasNext()
                ? mapper.apply(rowIterator.next())
                : null;
    }

<<<<<<< HEAD
=======
    /**
     * Returns succeeded {@link Future} if given value is not equal to NULL,
     * otherwise failed {@link Future} with {@link PreBidException}.
     */
    private static <T> Future<T> failedIfNull(T value, String id, String errorPrefix) {
        return value != null
                ? Future.succeededFuture(value)
                : Future.failedFuture(new PreBidException("%s not found: %s".formatted(errorPrefix, id)));
    }

>>>>>>> 04d9d4a13 (Initial commit)
    private Account toAccount(Row row) {
        final String source = ObjectUtil.getIfNotNull(row.getValue(0), Object::toString);
        try {
            return source != null ? mapper.decodeValue(source, Account.class) : null;
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage());
        }
    }

<<<<<<< HEAD
    @Override
    public Future<StoredDataResult<String>> getStoredData(String accountId,
                                                          Set<String> requestIds,
                                                          Set<String> impIds,
                                                          Timeout timeout) {

        return fetchStoredData(
                selectStoredRequestsQuery,
                requestIds,
                impIds,
                result -> DatabaseStoredDataResultMapper.map(result, accountId, requestIds, impIds),
                timeout);
    }

    @Override
    public Future<StoredDataResult<String>> getAmpStoredData(String accountId,
                                                             Set<String> requestIds,
                                                             Set<String> impIds,
                                                             Timeout timeout) {

        return fetchStoredData(
                selectAmpStoredRequestsQuery,
                requestIds,
                Collections.emptySet(),
                result -> DatabaseStoredDataResultMapper.map(result, accountId, requestIds, impIds),
                timeout);
    }

    @Override
    public Future<StoredDataResult<String>> getVideoStoredData(String accountId,
                                                               Set<String> requestIds,
                                                               Set<String> impIds,
                                                               Timeout timeout) {

        return fetchStoredData(
                selectStoredRequestsQuery,
                requestIds,
                impIds,
                result -> DatabaseStoredDataResultMapper.map(result, accountId, requestIds, impIds),
                timeout);
    }

    @Override
    public Future<StoredDataResult<Profile>> getProfiles(String accountId,
                                                         Set<String> requestIds,
                                                         Set<String> impIds,
                                                         Timeout timeout) {

        // TODO: remove in PBS 4.0
        if (selectProfilesQuery == null) {
            return Future.failedFuture("Profiles storage not configured.");
        }

        return fetchStoredData(
                selectProfilesQuery,
                requestIds,
                impIds,
                result -> DatabaseProfilesResultMapper.map(result, accountId, requestIds, impIds),
                timeout);
    }

    private <T> Future<StoredDataResult<T>> fetchStoredData(String query,
                                                            Set<String> requestIds,
                                                            Set<String> impIds,
                                                            Function<RowSet<Row>, StoredDataResult<T>> mapper,
                                                            Timeout timeout) {

        if (CollectionUtils.isEmpty(requestIds) && CollectionUtils.isEmpty(impIds)) {
            return Future.succeededFuture(StoredDataResult.of(
                    Collections.emptyMap(),
                    Collections.emptyMap(),
                    Collections.emptyList()));
        }

        final List<Object> idsQueryParameters = new ArrayList<>();
        IntStream.rangeClosed(1, StringUtils.countMatches(query, ParametrizedQueryHelper.REQUEST_ID_PLACEHOLDER))
                .forEach(i -> idsQueryParameters.addAll(requestIds));
        IntStream.rangeClosed(1, StringUtils.countMatches(query, ParametrizedQueryHelper.IMP_ID_PLACEHOLDER))
                .forEach(i -> idsQueryParameters.addAll(impIds));

        final String parametrizedQuery = parametrizedQueryHelper
                .replaceRequestAndImpIdPlaceholders(query, requestIds.size(), impIds.size());

        return databaseClient.executeQuery(parametrizedQuery, idsQueryParameters, mapper, timeout);
    }

    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        final String queryResolvedWithParameters = parametrizedQueryHelper
                .replaceStoredResponseIdPlaceholders(selectStoredResponsesQuery, responseIds.size());
=======
    /**
     * Runs a process to get stored requests by a collection of ids from database
     * and returns {@link Future}&lt;{@link StoredDataResult}&gt;.
     */
    @Override
    public Future<StoredDataResult> getStoredData(String accountId, Set<String> requestIds, Set<String> impIds,
                                                  Timeout timeout) {
        return fetchStoredData(selectStoredRequestsQuery, accountId, requestIds, impIds, timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of amp ids from database
     * and returns {@link Future}&lt;{@link StoredDataResult}&gt;.
     */
    @Override
    public Future<StoredDataResult> getAmpStoredData(String accountId, Set<String> requestIds, Set<String> impIds,
                                                     Timeout timeout) {
        return fetchStoredData(selectAmpStoredRequestsQuery, accountId, requestIds, Collections.emptySet(), timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of video ids from database
     * and returns {@link Future}&lt;{@link StoredDataResult}&gt;.
     */
    @Override
    public Future<StoredDataResult> getVideoStoredData(String accountId, Set<String> requestIds, Set<String> impIds,
                                                       Timeout timeout) {
        return fetchStoredData(selectStoredRequestsQuery, accountId, requestIds, impIds, timeout);
    }

    /**
     * Runs a process to get stored responses by a collection of ids from database
     * and returns {@link Future}&lt;{@link StoredResponseDataResult}&gt;.
     */
    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        final String queryResolvedWithParameters = parametrizedQueryHelper.replaceStoredResponseIdPlaceholders(
                selectStoredResponsesQuery,
                responseIds.size());
>>>>>>> 04d9d4a13 (Initial commit)

        final List<Object> idsQueryParameters = new ArrayList<>();
        final int responseIdPlaceholderCount = StringUtils.countMatches(
                selectStoredResponsesQuery,
                ParametrizedQueryHelper.RESPONSE_ID_PLACEHOLDER);
        IntStream.rangeClosed(1, responseIdPlaceholderCount)
                .forEach(i -> idsQueryParameters.addAll(responseIds));

<<<<<<< HEAD
        return databaseClient.executeQuery(
                queryResolvedWithParameters,
                idsQueryParameters,
                result -> DatabaseStoredResponseResultMapper.map(result, responseIds),
                timeout);
    }

    @Override
    public Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout) {
        return Future.failedFuture(new PreBidException("Not supported"));
=======
        return databaseClient.executeQuery(queryResolvedWithParameters, idsQueryParameters,
                result -> DatabaseStoredResponseResultMapper.map(result, responseIds), timeout);
    }

    /**
     * Fetches stored requests from database for the given query.
     */
    private Future<StoredDataResult> fetchStoredData(String query, String accountId, Set<String> requestIds,
                                                     Set<String> impIds, Timeout timeout) {
        final Future<StoredDataResult> future;

        if (CollectionUtils.isEmpty(requestIds) && CollectionUtils.isEmpty(impIds)) {
            future = Future.succeededFuture(
                    StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList()));
        } else {
            final List<Object> idsQueryParameters = new ArrayList<>();
            IntStream.rangeClosed(1, StringUtils.countMatches(query, ParametrizedQueryHelper.REQUEST_ID_PLACEHOLDER))
                    .forEach(i -> idsQueryParameters.addAll(requestIds));
            IntStream.rangeClosed(1, StringUtils.countMatches(query, ParametrizedQueryHelper.IMP_ID_PLACEHOLDER))
                    .forEach(i -> idsQueryParameters.addAll(impIds));

            final String parametrizedQuery = parametrizedQueryHelper.replaceRequestAndImpIdPlaceholders(
                    query,
                    requestIds.size(),
                    impIds.size());

            future = databaseClient.executeQuery(parametrizedQuery, idsQueryParameters,
                    result -> DatabaseStoredDataResultMapper.map(result, accountId, requestIds, impIds),
                    timeout);
        }

        return future;
>>>>>>> 04d9d4a13 (Initial commit)
    }
}
