package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.sql.ResultSet;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.mapper.JdbcStoredDataResultMapper;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.vertx.jdbc.JdbcClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Implementation of {@link ApplicationSettings}.
 * <p>
 * Reads an application settings from the database source.
 * <p>
 * In order to enable caching and reduce latency for read operations {@link JdbcApplicationSettings}
 * can be decorated by {@link CachingApplicationSettings}.
 */
public class JdbcApplicationSettings implements ApplicationSettings {

    private static final Logger logger = LoggerFactory.getLogger(JdbcApplicationSettings.class);

    private static final String REQUEST_ID_PLACEHOLDER = "%REQUEST_ID_LIST%";
    private static final String IMP_ID_PLACEHOLDER = "%IMP_ID_LIST%";

    private final JdbcClient jdbcClient;

    /**
     * Query to select stored requests and imps by ids, for example:
     * <pre>
     * SELECT reqid, requestData, 'request' as dataType
     *   FROM stored_requests
     *   WHERE reqid in (%REQUEST_ID_LIST%)
     * UNION ALL
     * SELECT impid, impData, 'imp' as dataType
     *   FROM stored_imps
     *   WHERE impid in (%IMP_ID_LIST%)
     * </pre>
     */
    private final String selectQuery;

    /**
     * Query to select amp stored requests by ids, for example:
     * <pre>
     * SELECT reqid, requestData, 'request' as dataType
     *   FROM stored_requests
     *   WHERE reqid in (%REQUEST_ID_LIST%)
     * </pre>
     */
    private final String selectAmpQuery;

    public JdbcApplicationSettings(JdbcClient jdbcClient, String selectQuery, String selectAmpQuery) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        this.selectQuery = Objects.requireNonNull(selectQuery);
        this.selectAmpQuery = Objects.requireNonNull(selectAmpQuery);
    }

    /**
     * Runs a process to get account by id from database
     * and returns {@link Future&lt;{@link Account}&gt;}
     */
    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return jdbcClient.executeQuery("SELECT uuid, price_granularity, banner_cache_ttl, video_cache_ttl,"
                        + " events_enabled FROM accounts_account where uuid = ? LIMIT 1",
                Collections.singletonList(accountId),
                result -> mapToModelOrError(result, row -> Account.of(row.getString(0), row.getString(1),
                        row.getInteger(2), row.getInteger(3), row.getBoolean(4))),
                timeout);
    }

    /**
     * Runs a process to get AdUnit config by id from database
     * and returns {@link Future&lt;{@link String}&gt;}
     */
    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId, Timeout timeout) {
        return jdbcClient.executeQuery("SELECT config FROM s2sconfig_config where uuid = ? LIMIT 1",
                Collections.singletonList(adUnitConfigId),
                result -> mapToModelOrError(result, row -> row.getString(0)),
                timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of ids from database
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}
     */
    @Override
    public Future<StoredDataResult> getStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return fetchStoredData(selectQuery, requestIds, impIds, timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of amp ids from database
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}
     */
    @Override
    public Future<StoredDataResult> getAmpStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return fetchStoredData(selectAmpQuery, requestIds, Collections.emptySet(), timeout);
    }

    /**
     * Transforms the first row of {@link ResultSet} to required object.
     */
    private <T> T mapToModelOrError(ResultSet result, Function<JsonArray, T> mapper) {
        if (result == null || result.getResults() == null || result.getResults().isEmpty()) {
            throw new PreBidException("Not found");
        }
        return mapper.apply(result.getResults().get(0));
    }

    /**
     * Fetches stored requests from database for the given query.
     */
    private Future<StoredDataResult> fetchStoredData(String query, Set<String> requestIds, Set<String> impIds,
                                                     Timeout timeout) {
        final Future<StoredDataResult> future;

        if (CollectionUtils.isEmpty(requestIds) && CollectionUtils.isEmpty(impIds)) {
            future = Future.succeededFuture(
                    StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList()));
        } else {
            final List<Object> idsQueryParameters = new ArrayList<>();
            IntStream.rangeClosed(1, StringUtils.countMatches(query, REQUEST_ID_PLACEHOLDER))
                    .forEach(i -> idsQueryParameters.addAll(requestIds));
            IntStream.rangeClosed(1, StringUtils.countMatches(query, IMP_ID_PLACEHOLDER))
                    .forEach(i -> idsQueryParameters.addAll(impIds));

            final String parametrizedQuery = createParametrizedQuery(query, requestIds.size(), impIds.size());
            future = jdbcClient.executeQuery(parametrizedQuery, idsQueryParameters,
                    result -> JdbcStoredDataResultMapper.map(result, requestIds, impIds),
                    timeout);
        }

        return future;
    }

    /**
     * Creates parametrized query from query and variable templates, by replacing templateVariable
     * with appropriate number of "?" placeholders.
     */
    private static String createParametrizedQuery(String query, int requestIdsSize, int impIdsSize) {
        return query
                .replace(REQUEST_ID_PLACEHOLDER, parameterHolders(requestIdsSize))
                .replace(IMP_ID_PLACEHOLDER, parameterHolders(impIdsSize));
    }

    /**
     * Returns string for parametrized placeholder
     */
    private static String parameterHolders(int paramsSize) {
        return paramsSize == 0
                ? "NULL"
                : IntStream.range(0, paramsSize).mapToObj(i -> "?").collect(Collectors.joining(","));
    }
}
