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
import org.prebid.server.settings.helper.DatabaseStoredDataResultMapper;
import org.prebid.server.settings.helper.DatabaseStoredResponseResultMapper;
import org.prebid.server.settings.helper.ParametrizedQueryHelper;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredProfileResult;
import org.prebid.server.settings.model.StoredResponseDataResult;
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
                                       String selectStoredResponsesQuery) {

        this.databaseClient = Objects.requireNonNull(databaseClient);
        this.mapper = Objects.requireNonNull(mapper);
        this.parametrizedQueryHelper = Objects.requireNonNull(parametrizedQueryHelper);
        this.selectAccountQuery = parametrizedQueryHelper.replaceAccountIdPlaceholder(
                Objects.requireNonNull(selectAccountQuery));
        this.selectStoredRequestsQuery = Objects.requireNonNull(selectStoredRequestsQuery);
        this.selectAmpStoredRequestsQuery = Objects.requireNonNull(selectAmpStoredRequestsQuery);
        this.selectStoredResponsesQuery = Objects.requireNonNull(selectStoredResponsesQuery);
    }

    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return databaseClient.executeQuery(
                        selectAccountQuery,
                        Collections.singletonList(accountId),
                        result -> mapToModelOrError(result, this::toAccount),
                        timeout)
                .compose(result -> result != null
                        ? Future.succeededFuture(result)
                        : Future.failedFuture(new PreBidException("Account not found: " + accountId)));
    }

    /**
     * Note: mapper should never throw exception in case of using
     * {@link CircuitBreakerSecuredDatabaseClient}.
     */
    private <T> T mapToModelOrError(RowSet<Row> rowSet, Function<Row, T> mapper) {
        final RowIterator<Row> rowIterator = rowSet != null ? rowSet.iterator() : null;
        return rowIterator != null && rowIterator.hasNext()
                ? mapper.apply(rowIterator.next())
                : null;
    }

    private Account toAccount(Row row) {
        final String source = Objects.toString(row.getValue(0), null);
        try {
            return source != null ? mapper.decodeValue(source, Account.class) : null;
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    @Override
    public Future<StoredDataResult> getStoredData(String accountId,
                                                  Set<String> requestIds,
                                                  Set<String> impIds,
                                                  Timeout timeout) {

        return fetchStoredData(selectStoredRequestsQuery, accountId, requestIds, impIds, timeout);
    }

    @Override
    public Future<StoredDataResult> getAmpStoredData(String accountId,
                                                     Set<String> requestIds,
                                                     Set<String> impIds,
                                                     Timeout timeout) {

        return fetchStoredData(selectAmpStoredRequestsQuery, accountId, requestIds, Collections.emptySet(), timeout);
    }

    @Override
    public Future<StoredDataResult> getVideoStoredData(String accountId,
                                                       Set<String> requestIds,
                                                       Set<String> impIds,
                                                       Timeout timeout) {

        return fetchStoredData(selectStoredRequestsQuery, accountId, requestIds, impIds, timeout);
    }

    private Future<StoredDataResult> fetchStoredData(String query,
                                                     String accountId,
                                                     Set<String> requestIds,
                                                     Set<String> impIds,
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

        return databaseClient.executeQuery(
                parametrizedQuery,
                idsQueryParameters,
                result -> DatabaseStoredDataResultMapper.map(result, accountId, requestIds, impIds),
                timeout);
    }

    @Override
    public Future<StoredProfileResult> getProfiles(String accountId,
                                                   Set<String> requestIds,
                                                   Set<String> impIds,
                                                   Timeout timeout) {

        // TODO: query?
        return Future.failedFuture("Not implemented");
    }

    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        final String queryResolvedWithParameters = parametrizedQueryHelper
                .replaceStoredResponseIdPlaceholders(selectStoredResponsesQuery, responseIds.size());

        final List<Object> idsQueryParameters = new ArrayList<>();
        final int responseIdPlaceholderCount = StringUtils.countMatches(
                selectStoredResponsesQuery,
                ParametrizedQueryHelper.RESPONSE_ID_PLACEHOLDER);
        IntStream.rangeClosed(1, responseIdPlaceholderCount)
                .forEach(i -> idsQueryParameters.addAll(responseIds));

        return databaseClient.executeQuery(
                queryResolvedWithParameters,
                idsQueryParameters,
                result -> DatabaseStoredResponseResultMapper.map(result, responseIds),
                timeout);
    }

    @Override
    public Future<Map<String, String>> getCategories(String primaryAdServer, String publisher, Timeout timeout) {
        return Future.failedFuture(new PreBidException("Not supported"));
    }
}
