package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.sql.ResultSet;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.vertx.JdbcClient;

import java.util.Collections;
import java.util.Objects;
import java.util.function.Function;

public class JdbcApplicationSettings implements ApplicationSettings {

    private final JdbcClient jdbcClient;

    public JdbcApplicationSettings(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient);
    }

    @Override
    public Future<Account> getAccountById(String accountId, GlobalTimeout timeout) {
        Objects.requireNonNull(accountId);
        Objects.requireNonNull(timeout);
        return jdbcClient.executeQuery("SELECT uuid, price_granularity FROM accounts_account where uuid = ? LIMIT 1",
                Collections.singletonList(accountId),
                result -> mapToModelOrError(result, row -> Account.of(row.getString(0), row.getString(1))),
                timeout);
    }

    @Override
    public Future<String> getAdUnitConfigById(String adUnitConfigId, GlobalTimeout timeout) {
        Objects.requireNonNull(adUnitConfigId);
        Objects.requireNonNull(timeout);
        return jdbcClient.executeQuery("SELECT config FROM s2sconfig_config where uuid = ? LIMIT 1",
                Collections.singletonList(adUnitConfigId),
                result -> mapToModelOrError(result, row -> row.getString(0)),
                timeout);
    }

    private <T> T mapToModelOrError(ResultSet result, Function<JsonArray, T> mapper) {
        if (result == null || result.getResults() == null || result.getResults().isEmpty()) {
            throw new PreBidException("Not found");
        } else {
            return mapper.apply(result.getResults().get(0));
        }
    }
}
