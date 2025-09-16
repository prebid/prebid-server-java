package org.prebid.server.vertx.database;

import io.vertx.core.Future;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import org.prebid.server.execution.timeout.Timeout;

import java.util.List;
import java.util.function.Function;

/**
 * Interface for asynchronous interaction with database over database API.
 */
@FunctionalInterface
public interface DatabaseClient {

    /**
     * Executes query with parameters and returns {@link Future<T>} eventually holding result mapped to a model
     * object by provided mapper.
     */
    <T> Future<T> executeQuery(String query, List<Object> params, Function<RowSet<Row>, T> mapper, Timeout timeout);
}
