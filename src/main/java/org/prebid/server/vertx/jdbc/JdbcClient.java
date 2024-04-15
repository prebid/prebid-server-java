package org.prebid.server.vertx.jdbc;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.prebid.server.execution.Timeout;

import java.util.List;
import java.util.function.Function;

/**
 * Interface for asynchronous interaction with database over JDBC API.
 */
@FunctionalInterface
public interface JdbcClient {

    /**
     * Executes query with parameters and returns {@link Future<T>} eventually holding result mapped to a model
     * object by provided mapper.
     */
    <T> Future<T> executeQuery(String query, List<Object> params, Function<RowSet<Row>, T> mapper, Timeout timeout);
}
