package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.settings.helper.JdbcQueryTranslator;
import org.prebid.server.settings.helper.JdbcStoredResponseResultMapper;
import org.prebid.server.settings.jdbc.model.SqlQuery;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;
import org.prebid.server.settings.model.AccountBidValidationConfig;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;
import org.prebid.server.vertx.jdbc.JdbcClient;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Implementation of {@link ApplicationSettings}.
 * <p>
 * Reads an application settings from the database source.
 * <p>
 * In order to enable caching and reduce latency for read operations {@link JdbcApplicationSettings}
 * can be decorated by {@link CachingApplicationSettings}.
 */
public class JdbcApplicationSettings implements ApplicationSettings {

    private final JdbcQueryTranslator jdbcQueryTranslator;
    private final JdbcClient jdbcClient;
    private final JacksonMapper mapper;

    public JdbcApplicationSettings(JdbcQueryTranslator jdbcQueryTranslator,
                                   JdbcClient jdbcClient,
                                   JacksonMapper mapper) {

        this.jdbcQueryTranslator = Objects.requireNonNull(jdbcQueryTranslator);
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Runs a process to get account by id from database
     * and returns {@link Future&lt;{@link Account}&gt;}.
     */
    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        final SqlQuery query = jdbcQueryTranslator.selectAccountQuery(accountId);

        return jdbcClient.executeQuery(
                query.getQuery(),
                query.getParameters(),
                result -> mapToModelOrError(result, row -> Account.builder()
                        .id(row.getString(0))
                        .priceGranularity(row.getString(1))
                        .bannerCacheTtl(row.getInteger(2))
                        .videoCacheTtl(row.getInteger(3))
                        .eventsEnabled(row.getBoolean(4))
                        .enforceCcpa(row.getBoolean(5))
                        .gdpr(toModel(row.getString(6), AccountGdprConfig.class))
                        .analyticsSamplingFactor(row.getInteger(7))
                        .truncateTargetAttr(row.getInteger(8))
                        .defaultIntegration(row.getString(9))
                        .analyticsConfig(toModel(row.getString(10), AccountAnalyticsConfig.class))
                        .bidValidations(toModel(row.getString(11), AccountBidValidationConfig.class))
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
        final SqlQuery query = jdbcQueryTranslator.selectAdUnitConfigQuery(adUnitConfigId);

        return jdbcClient.executeQuery(
                query.getQuery(),
                query.getParameters(),
                result -> mapToModelOrError(result, row -> row.getString(0)),
                timeout)
                .compose(result -> failedIfNull(result, adUnitConfigId, "AdUnitConfig"));
    }

    private <T> T toModel(String source, Class<T> targetClass) {
        try {
            return source != null ? mapper.decodeValue(source, targetClass) : null;
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    /**
     * Runs a process to get stored requests by a collection of ids from database
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}.
     */
    @Override
    public Future<StoredDataResult> getStoredData(String accountId,
                                                  Set<String> requestIds,
                                                  Set<String> impIds,
                                                  Timeout timeout) {

        return fetchStoredData(jdbcQueryTranslator::selectStoredRequestsQuery, accountId, requestIds, impIds, timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of amp ids from database
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}.
     */
    @Override
    public Future<StoredDataResult> getAmpStoredData(String accountId,
                                                     Set<String> requestIds,
                                                     Set<String> impIds,
                                                     Timeout timeout) {

        return fetchStoredData(
                jdbcQueryTranslator::selectAmpStoredRequestsQuery,
                accountId,
                requestIds,
                Collections.emptySet(),
                timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of video ids from database
     * and returns {@link Future&lt;{@link StoredDataResult }&gt;}.
     */
    @Override
    public Future<StoredDataResult> getVideoStoredData(String accountId,
                                                       Set<String> requestIds,
                                                       Set<String> impIds,
                                                       Timeout timeout) {

        return fetchStoredData(jdbcQueryTranslator::selectStoredRequestsQuery, accountId, requestIds, impIds, timeout);
    }

    /**
     * Runs a process to get stored responses by a collection of ids from database
     * and returns {@link Future&lt;{@link StoredResponseDataResult }&gt;}.
     */
    @Override
    public Future<StoredResponseDataResult> getStoredResponses(Set<String> responseIds, Timeout timeout) {
        final SqlQuery query = jdbcQueryTranslator.selectStoredResponsesQuery(responseIds);

        return jdbcClient.executeQuery(
                query.getQuery(),
                query.getParameters(),
                result -> JdbcStoredResponseResultMapper.map(result, responseIds),
                timeout);
    }

    /**
     * Transforms the first row of {@link ResultSet} to required object or returns null.
     * <p>
     * Note: mapper should never throws exception in case of using
     * {@link org.prebid.server.vertx.jdbc.CircuitBreakerSecuredJdbcClient}.
     */
    private static <T> T mapToModelOrError(ResultSet result, Function<JsonArray, T> mapper) {
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
     * Fetches stored requests from database for the given query.
     */
    private Future<StoredDataResult> fetchStoredData(BiFunction<Set<String>, Set<String>, SqlQuery> querySupplier,
                                                     String accountId,
                                                     Set<String> requestIds,
                                                     Set<String> impIds,
                                                     Timeout timeout) {

        if (CollectionUtils.isEmpty(requestIds) && CollectionUtils.isEmpty(impIds)) {
            return Future.succeededFuture(
                    StoredDataResult.of(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyList()));
        }

        final SqlQuery query = querySupplier.apply(requestIds, impIds);

        return jdbcClient.executeQuery(
                query.getQuery(),
                query.getParameters(),
                result -> jdbcQueryTranslator.translateQueryResultToStoredData(result, accountId, requestIds, impIds),
                timeout);
    }
}
