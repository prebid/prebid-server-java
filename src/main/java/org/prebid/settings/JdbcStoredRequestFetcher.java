package org.prebid.settings;

import io.vertx.core.Future;
import io.vertx.ext.sql.ResultSet;
import org.apache.commons.lang3.StringUtils;
import org.prebid.execution.GlobalTimeout;
import org.prebid.settings.model.StoredRequestResult;
import org.prebid.vertx.JdbcClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Executes stored requests fetching from database source.
 */
public class JdbcStoredRequestFetcher implements StoredRequestFetcher {

    private static final String ID_PLACEHOLDER = "%ID_LIST%";

    private final JdbcClient jdbcClient;
    private final String selectQuery;
    private final String selectAmpQuery;

    public JdbcStoredRequestFetcher(JdbcClient jdbcClient, String selectQuery, String selectAmpQuery) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
        this.selectQuery = Objects.requireNonNull(selectQuery);
        this.selectAmpQuery = Objects.requireNonNull(selectAmpQuery);
    }

    /**
     * Runs a process to get StoredRequest by ids from database and returns {@link Future< StoredRequestResult >}
     */
    @Override
    public Future<StoredRequestResult> getStoredRequestsById(Set<String> ids, GlobalTimeout timeout) {
        return fetchStoredRequests(selectQuery, ids, timeout);
    }

    /**
     * Runs a process to get StoredRequest by amp ids from database and returns {@link Future<StoredRequestResult>}
     */
    @Override
    public Future<StoredRequestResult> getStoredRequestsByAmpId(Set<String> ids, GlobalTimeout timeout) {
        return fetchStoredRequests(selectAmpQuery, ids, timeout);
    }

    /**
     * Fetches stored requests from database for the given query.
     */
    private Future<StoredRequestResult> fetchStoredRequests(String query, Set<String> ids, GlobalTimeout timeout) {
        Objects.requireNonNull(ids);
        Objects.requireNonNull(timeout);

        final List<String> idsQueryParameters = new ArrayList<>();
        IntStream.rangeClosed(1, StringUtils.countMatches(query, ID_PLACEHOLDER))
                .forEach(i -> idsQueryParameters.addAll(ids));

        return jdbcClient.executeQuery(createParametrizedQuery(query, ids.size()), idsQueryParameters,
                result -> mapToModel(result, ids),
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
    private static StoredRequestResult mapToModel(ResultSet rs, Set<String> ids) {
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
