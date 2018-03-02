package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.settings.model.Account;
import org.prebid.server.vertx.JdbcClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@RunWith(VertxUnitRunner.class)
public class JdbcApplicationSettingsTest {

    private static final String JDBC_URL = "jdbc:h2:mem:test";

    private static Connection connection;

    private Vertx vertx;

    private JdbcApplicationSettings jdbcApplicationSettings;

    @BeforeClass
    public static void beforeClass() throws SQLException {
        connection = DriverManager.getConnection(JDBC_URL);
        connection.createStatement().execute("CREATE TABLE accounts_account (id SERIAL PRIMARY KEY, uuid varchar(40) " +
                "NOT NULL, price_granularity varchar(6), granularityMultiplier numeric(9,3));");
        connection.createStatement().execute("CREATE TABLE s2sconfig_config (id SERIAL PRIMARY KEY, uuid varchar(40) " +
                "NOT NULL, config varchar(512));");
        connection.createStatement().execute("insert into accounts_account (uuid, price_granularity)" +
                " values ('accountId','med');");
        connection.createStatement().execute("insert into s2sconfig_config (uuid, config)" +
                " values ('adUnitConfigId', 'config');");
    }

    @AfterClass
    public static void afterClass() throws SQLException {
        connection.close();
    }

    @Before
    public void setUp() {
        vertx = Vertx.vertx();

        this.jdbcApplicationSettings = new JdbcApplicationSettings(new JdbcClient(vertx, JDBCClient.createShared(vertx,
                new JsonObject()
                        .put("url", JDBC_URL)
                        .put("driver_class", "org.h2.Driver")
                        .put("max_pool_size", 10))));
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new JdbcApplicationSettings(null));
    }

    @Test
    public void getAccountByIdShouldReturnAccount(TestContext context) {
        // when
        final Future<Account> future = jdbcApplicationSettings.getAccountById("accountId", timeout());

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(account -> {
            assertThat(account).isEqualTo(Account.of("accountId", "med"));
            async.complete();
        }));
    }

    @Test
    public void getAccountByIdShouldFailIfAccountNotFound(TestContext context) {
        // when
        final Future<Account> future = jdbcApplicationSettings.getAccountById("non-existing", timeout());

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertFailure(exception -> {
            assertThat(exception).isInstanceOf(PreBidException.class).hasMessage("Not found");
            async.complete();
        }));
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnConfig(TestContext context) {
        // when
        final Future<String> future = jdbcApplicationSettings.getAdUnitConfigById("adUnitConfigId", timeout());

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(config -> {
            assertThat(config).isEqualTo("config");
            async.complete();
        }));
    }

    @Test
    public void getAdUnitConfigByIdShouldFailIfConfigNotFound(TestContext context) {
        // when
        final Future<String> future = jdbcApplicationSettings.getAdUnitConfigById("non-existing", timeout());

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertFailure(exception -> {
            assertThat(exception).isInstanceOf(PreBidException.class).hasMessage("Not found");
            async.complete();
        }));
    }

    private static GlobalTimeout timeout() {
        return GlobalTimeout.create(500);
    }
}
