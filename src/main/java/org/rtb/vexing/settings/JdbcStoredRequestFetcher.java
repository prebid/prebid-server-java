package org.rtb.vexing.settings;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.settings.model.StoredRequestResult;
import org.rtb.vexing.spring.config.StoredRequestProperties;

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

    private final JDBCClient jdbcClient;
    private final String selectQuery;

    private JdbcStoredRequestFetcher(JDBCClient jdbcClient, String selectQuery) {
        this.jdbcClient = jdbcClient;
        this.selectQuery = selectQuery;
    }

    /**
     * Creates {@link JdbcStoredRequestFetcher} instance, query test connection simple select and returns
     * {@link JdbcStoredRequestFetcher}
     */
    public static JdbcStoredRequestFetcher create(Vertx vertx, String url, String driverClass, int maxPoolSize,
                                                          String selectQuery) {
        Objects.requireNonNull(vertx);
        Objects.requireNonNull(url);
        Objects.requireNonNull(driverClass);
        Objects.requireNonNull(selectQuery);
        if (maxPoolSize <= 0) {
            throw new IllegalArgumentException("maxPoolSize must be positive");
        }

        final JDBCClient jdbcClient = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", url)
                .put("driver_class", driverClass)
                .put("max_pool_size", maxPoolSize));

        return new JdbcStoredRequestFetcher(jdbcClient, selectQuery);
    }

    /**
     * Runs a process to get StoredRequest ids from database and returns {@link Future<StoredRequestResult>}
     */
    @Override
    public Future<StoredRequestResult> getStoredRequestsById(Set<String> ids) {
        Objects.requireNonNull(ids);
        final Future<SQLConnection> connectionFuture = Future.future();
        jdbcClient.getConnection(connectionFuture.completer());
        final List<String> idsQueryParameters = new ArrayList<>();
        IntStream.rangeClosed(1, StringUtils.countMatches(selectQuery, ID_PLACEHOLDER))
                .forEach(i -> idsQueryParameters.addAll(ids));

        return connectionFuture
                .compose(connection -> executeQueryWithParam(connection, createParametrizedQuery(ids.size()),
                        idsQueryParameters))
                .map(resultSet -> mapResultSetToStoredRequestResult(resultSet, ids));
    }

    /**
     * Start acquiring a connection
     */
    @Override
    public Future<Void> initialize() {
        final Future<ResultSet> result = Future.future();
        jdbcClient.query("SELECT 1", result.completer());
        return result.map(ignored -> null);
    }

    /**
     * Creates parametrized query from query and variable templates, by replacing templateVariable
     * with appropriate number of "?" placeholders.
     */
    private String createParametrizedQuery(int size) {

        final String parameters = IntStream.range(0, size).mapToObj(i -> "?").collect(Collectors.joining(","));
        return selectQuery.replace(ID_PLACEHOLDER, parameters);
    }

    /**
     * Executes query with parameters and returns {@link Future<ResultSet>}
     */
    private Future<ResultSet> executeQueryWithParam(SQLConnection connection, String parametrizedQuery,
                                                    List<String> ids) {
        final Future<ResultSet> resultSetFuture = Future.future();
        connection.queryWithParams(parametrizedQuery, new JsonArray(ids),
                ar -> {
                    connection.close();
                    resultSetFuture.handle(ar);
                });
        return resultSetFuture;
    }

    /**
     * Maps {@link ResultSet} to {@link StoredRequestResult}. In case of {@link ResultSet} size is less than ids number
     * creates an error for each missing id and add it to result.
     */
    private StoredRequestResult mapResultSetToStoredRequestResult(ResultSet rs, Set<String> ids) {
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

    /**
     * Creates String representation of jdbc configuration from StoredRequestProperties{@link StoredRequestProperties}
     */
    public static String jdbcUrl(StoredRequestProperties properties, String protocol) {
        return String.format("%s//%s/%s?user=%s&password=%s&useSSL=false",
                protocol,
                Objects.requireNonNull(properties.getHost(), message("host")),
                Objects.requireNonNull(properties.getDbname(), message("dbname")),
                Objects.requireNonNull(properties.getUser(), message("user")),
                Objects.requireNonNull(properties.getPassword(), message("password")));
    }

    static String message(String field) {
        return String.format("Configuration property stored-requests.%s is missing", field);
    }
}
