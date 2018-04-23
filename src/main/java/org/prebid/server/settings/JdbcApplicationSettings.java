package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredRequestResult;
import org.prebid.server.vertx.JdbcClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Implementation of {@link ApplicationSettings}.
 * <p>
 * Reads an application settings from the database source. In order to enable caching and reduce latency
 * for read operations {@link JdbcApplicationSettings} can be decorated by {@link CachingApplicationSettings}.
 */
public class JdbcApplicationSettings implements ApplicationSettings {

    private static final String ID_PLACEHOLDER = "%ID_LIST%";

    private final JdbcClient jdbcClient;
    private final String selectStoredRequestsQuery;
    private final String selectAmpStoredRequestsQuery;

    public JdbcApplicationSettings(JdbcClient jdbcClient, String selectQuery, String selectAmpQuery) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        this.selectStoredRequestsQuery = Objects.requireNonNull(selectQuery);
        this.selectAmpStoredRequestsQuery = Objects.requireNonNull(selectAmpQuery);
    }

    /**
     * Runs a process to get account by id from database
     * and returns {@link Future&lt;{@link Account}&gt;}
     */
    @Override
    public Future<Account> getAccountById(String accountId, Timeout timeout) {
        return jdbcClient.executeQuery("SELECT uuid, price_granularity FROM accounts_account where uuid = ? LIMIT 1",
                Collections.singletonList(accountId),
                result -> mapToModelOrError(result, row -> Account.of(row.getString(0), row.getString(1))),
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
     * and returns {@link Future&lt;{@link StoredRequestResult}&gt;}
     */
    @Override
    public Future<StoredRequestResult> getStoredRequestsById(Set<String> ids, Timeout timeout) {
        return fetchStoredRequests(selectStoredRequestsQuery, ids, timeout);
    }

    /**
     * Runs a process to get stored requests by a collection of amp ids from database
     * and returns {@link Future&lt;{@link StoredRequestResult}&gt;}
     */
    @Override
    public Future<StoredRequestResult> getStoredRequestsByAmpId(Set<String> ids, Timeout timeout) {
        return fetchStoredRequests(selectAmpStoredRequestsQuery, ids, timeout);
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
    private Future<StoredRequestResult> fetchStoredRequests(String query, Set<String> ids, Timeout timeout) {
        final List<String> idsQueryParameters = new ArrayList<>();
        IntStream.rangeClosed(1, StringUtils.countMatches(query, ID_PLACEHOLDER))
                .forEach(i -> idsQueryParameters.addAll(ids));

        return jdbcClient.executeQuery(createParametrizedQuery(query, ids.size()), idsQueryParameters,
                result -> mapToStoreRequestResult(result, ids),
                timeout);
    }

    /**
     * Creates parametrized query from query and variable templates, by replacing templateVariable
     * with appropriate number of "?" placeholders.
     */
    private static String createParametrizedQuery(String query, int size) {
        final String parameters = IntStream.range(0, size).mapToObj(i -> "?").collect(Collectors.joining(","));
        return query.replace(ID_PLACEHOLDER, parameters);
    }

    /**
     * Maps {@link ResultSet} to {@link StoredRequestResult}. In case of {@link ResultSet} size is less than ids number
     * creates an error for each missing id and add it to result.
     */
    private static StoredRequestResult mapToStoreRequestResult(ResultSet rs, Set<String> ids) {
        final List<String> errors = new ArrayList<>();
        final Map<String, String> storedIdToJson;

        if (rs == null || rs.getResults() == null || rs.getResults().isEmpty()) {
            errors.add(String.format("Stored requests for ids %s was not found", ids));
            storedIdToJson = Collections.emptyMap();
        } else {
            try {
                storedIdToJson = rs.getResults().stream()
                        .collect(Collectors.toMap(result -> result.getString(0), result -> result.getString(1)));
            } catch (IndexOutOfBoundsException ex) {
                errors.add("Result set column number is less than expected");
                return StoredRequestResult.of(Collections.emptyMap(), errors);
            }

            if (storedIdToJson.size() < ids.size()) {
                final Set<String> missedIds = new HashSet<>(ids);
                missedIds.removeAll(storedIdToJson.keySet());

                errors.addAll(missedIds.stream()
                        .map(id -> String.format("No config found for id: %s", id))
                        .collect(Collectors.toList()));
            }
        }

        return StoredRequestResult.of(storedIdToJson, errors);
    }
}
