package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.mapper.JdbcStoredDataResultMapper;
import org.prebid.server.settings.mapper.JdbcStoredResponseResultMapper;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;
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

    private static final String REQUEST_ID_PLACEHOLDER = "%REQUEST_ID_LIST%";
    private static final String IMP_ID_PLACEHOLDER = "%IMP_ID_LIST%";
    private static final String RESPONSE_ID_PLACEHOLDER = "%RESPONSE_ID_LIST%";

    private final JdbcClient jdbcClient;
    private final JacksonMapper mapper;

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

    /**
     * Query to select stored responses by ids, for example:
     * <pre>
     * SELECT respid, responseData
     *   FROM stored_responses
     *   WHERE respid in (%RESPONSE_ID_LIST%)
     * </pre>
     */
    private final String selectResponseQuery;

    public JdbcApplicationSettings(JdbcClient jdbcClient,
                                   JacksonMapper mapper,
                                   String selectQuery,
                                   String selectAmpQuery,
                                   String selectResponseQuery) {

        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        this.mapper = Objects.requireNonNull(mapper);
        this.selectQuery = Objects.requireNonNull(selectQuery);
        this.selectAmpQuery = Objects.requireNonNull(selectAmpQuery);
        this.selectResponseQuery = Objects.requireNonNull(selectResponseQuery);
    }

    /**
     * Runs a process to get account by id from database
     * and returns {@link Future&lt;{@link Account}&gt;}.
     */
    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return jdbcClient.executeQuery("SELECT uuid, price_granularity, banner_cache_ttl, video_cache_ttl,"
                        + " events_enabled, enforce_gdpr, tcf_config, analytics_sampling_factor FROM accounts_account"
                        + " where uuid = ? LIMIT 1",
                Collections.singletonList(accountId),
                result -> mapToModelOrError(result, row -> Account.builder()
                        .id(row.getString(0))
                        .priceGranularity(row.getString(1))
                        .bannerCacheTtl(row.getInteger(2))
                        .videoCacheTtl(row.getInteger(3))
                        .eventsEnabled(row.getBoolean(4))
                        .enforceGdpr(row.getBoolean(5))
                        .gdpr(toAccountTcfConfig(row.getString(6)))
                        .analyticsSamplingFactor(row.getInteger(7))
                        .build()),
                timeout)
                .compose(result -> failedIfNull(result, accountId, "Account"));
    }

    /**
     * Runs a process to get AdUnit config by id from database
     * and returns {@link Future&lt;{@link String}&gt;}.
     */
    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId, Timeout timeout) {
        return jdbcClient.executeQuery("SELECT config FROM s2sconfig_config where uuid = ? LIMIT 1",
                Collections.singletonList(adUnitConfigId),
                result -> mapToModelOrError(result, row -> row.getString(0)),
                timeout)
                .compose(result -> failedIfNull(result, adUnitConfigId, "AdUnitConfig"));
    }

    /**
     * Transforms the first row of {@link ResultSet} to required object or returns null.
     * <p>
     * Note: mapper should never throws exception in case of using
     * {@link org.prebid.server.vertx.jdbc.CircuitBreakerSecuredJdbcClient}.
     */
    private <T> T mapToModelOrError(ResultSet result, Function<JsonArray, T> mapper) {
        return result != null && CollectionUtils.isNotEmpty(result.getResults())
                ? mapper.apply(result.getResults().get(0))
                : null;
    }

    /**
     * Returns succeeded {@link Future} if given value is not equal to NULL,
     * otherwise failed {@link Future} with {@link PreBidException}.
     */
    private static <T> Future<T> failedIfNull(T value, String id, String errorPrefix) {
        return value != null
                ? Future.succeededFuture(value)
                : Future.failedFuture(new PreBidException(String.format("%s not found: %s", errorPrefix, id)));
    }

    /**
     * Runs a process to get stored requests by a collection of ids from database
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}.
     */
    @Override
    public Future<StoredDataResult> getStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return fetchStoredData(selectQuery, requestIds, impIds, timeout);
    }

    /**
     * Runs a process to get stored responses by a collection of ids from database
     * and returns {@link Future&lt;{@link StoredResponseDataResult }&gt;}.
     */
    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        final String queryResolvedWithParameters = selectResponseQuery.replaceAll(RESPONSE_ID_PLACEHOLDER,
                parameterHolders(responseIds.size()));

        final List<Object> idsQueryParameters = new ArrayList<>();
        IntStream.rangeClosed(1, StringUtils.countMatches(selectResponseQuery, RESPONSE_ID_PLACEHOLDER))
                .forEach(i -> idsQueryParameters.addAll(responseIds));

        return jdbcClient.executeQuery(queryResolvedWithParameters, idsQueryParameters,
                result -> JdbcStoredResponseResultMapper.map(result, responseIds), timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of amp ids from database
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}.
     */
    @Override
    public Future<StoredDataResult> getAmpStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return fetchStoredData(selectAmpQuery, requestIds, Collections.emptySet(), timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of video ids from database
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}.
     */
    @Override
    public Future<StoredDataResult> getVideoStoredData(Set<String> requestIds, Set<String> impIds, Timeout timeout) {
        return fetchStoredData(selectQuery, requestIds, impIds, timeout);
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
     * Returns string for parametrized placeholder.
     */
    private static String parameterHolders(int paramsSize) {
        return paramsSize == 0
                ? "NULL"
                : IntStream.range(0, paramsSize).mapToObj(i -> "?").collect(Collectors.joining(","));
    }

    private AccountGdprConfig toAccountTcfConfig(String tcfConfig) {
        try {
            return tcfConfig != null ? mapper.decodeValue(tcfConfig, AccountGdprConfig.class) : null;
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage());
        }
    }
}
