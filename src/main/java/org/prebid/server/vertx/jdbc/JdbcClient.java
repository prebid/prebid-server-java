package org.prebid.server.vertx.jdbc;

import io.vertx.core.Future;
import io.vertx.ext.sql.ResultSet;
import org.prebid.server.execution.Timeout;

import java.util.List;
import java.util.function.Function;

/**
 * Interface for asynchronous interaction with database over JDBC API
 */
public interface JdbcClient {

    /**
     * Triggers connection creation. Should be called during application initialization to detect connection issues as
     * early as possible.
     * <p>
     * Must be called on Vertx event loop thread.
     */
    Future<Void> initialize();

    /**
     * Executes query with parameters and returns {@link Future<T>} eventually holding result mapped to a model
     * object by provided mapper
     */
    <T> Future<T> executeQuery(String query, List<String> params, Function<ResultSet, T> mapper, Timeout timeout);
}
