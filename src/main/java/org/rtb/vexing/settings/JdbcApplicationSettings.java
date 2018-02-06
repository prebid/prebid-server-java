package org.rtb.vexing.settings;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.settings.model.Account;

import java.util.Objects;
import java.util.function.Function;

public class JdbcApplicationSettings implements ApplicationSettings {

    private final JDBCClient jdbcClient;

    public JdbcApplicationSettings(JDBCClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
    }

    @Override
    public Future<Account> getAccountById(String accountId) {
        Objects.requireNonNull(accountId);
        return executeQuery("SELECT uuid, price_granularity FROM accounts_account where uuid = ? LIMIT 1", accountId,
                result -> Account.builder()
                        .id(result.getString(0))
                        .priceGranularity(result.getString(1))
                        .build());
    }

    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId) {
        Objects.requireNonNull(adUnitConfigId);
        return executeQuery("SELECT config FROM s2sconfig_config where uuid = ? LIMIT 1", adUnitConfigId,
                result -> result.getString(0));
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

    private <T> Future<T> executeQuery(String query, String key, Function<JsonArray, T> mapper) {
        Objects.requireNonNull(key);

        final Future<SQLConnection> connectionFuture = Future.future();
        jdbcClient.getConnection(connectionFuture.completer());

        return connectionFuture
                .compose(connection -> {
                    final Future<ResultSet> resultSetFuture = Future.future();
                    connection.queryWithParams(query, new JsonArray().add(key),
                            ar -> {
                                connection.close();
                                resultSetFuture.handle(ar);
                            });
                    return resultSetFuture;
                })
                .map(rs -> {
                    if (rs == null || rs.getResults() == null || rs.getResults().isEmpty()) {
                        throw new PreBidException("Not found");
                    } else {
                        return mapper.apply(rs.getResults().get(0));
                    }
                });
    }
}
