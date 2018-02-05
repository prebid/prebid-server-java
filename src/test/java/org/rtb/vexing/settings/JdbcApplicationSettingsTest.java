package org.rtb.vexing.settings;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.rtb.vexing.exception.PreBidException;
import org.rtb.vexing.settings.model.Account;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;

@RunWith(VertxUnitRunner.class)
public class JdbcApplicationSettingsTest {

    private static final String JDBC_URL = "jdbc:h2:mem:test";
    private Vertx vertx;

    private JdbcApplicationSettings jdbcApplicationSettings;

    private static Connection connection;

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
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();

        this.jdbcApplicationSettings = JdbcApplicationSettings.create(vertx, JDBC_URL, "org.h2.Driver", 10);
    }

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> JdbcApplicationSettings.create(null, null, null, 0));
        assertThatNullPointerException().isThrownBy(() -> JdbcApplicationSettings.create(vertx, null, null, 0));
        assertThatNullPointerException().isThrownBy(() -> JdbcApplicationSettings.create(vertx, "url", null, 0));
        assertThatIllegalArgumentException().isThrownBy(
                () -> JdbcApplicationSettings.create(vertx, "url", "driverClass", 0))
                .withMessage("maxPoolSize must be positive");
    }

    @Test
    public void getAccountByIdShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> jdbcApplicationSettings.getAccountById(null));
    }

    @Test
    public void getAccountByIdShouldReturnAccount(TestContext context) {
        // when
        final Future<Account> future = jdbcApplicationSettings.getAccountById("accountId");

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(account -> {
            assertThat(account).isEqualTo(Account.builder().id("accountId").priceGranularity("med").build());
            async.complete();
        }));
    }

    @Test
    public void getAccountByIdShouldFailIfAccountNotFound(TestContext context) {
        // when
        final Future<Account> future = jdbcApplicationSettings.getAccountById("non-existing");

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertFailure(exception -> {
            assertThat(exception).isInstanceOf(PreBidException.class).hasMessage("Not found");
            async.complete();
        }));
    }

    @Test
    public void getAdUnitConfigByIdShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> jdbcApplicationSettings.getAdUnitConfigById(null));
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnConfig(TestContext context) {
        // when
        final Future<String> future = jdbcApplicationSettings.getAdUnitConfigById("adUnitConfigId");

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
        final Future<String> future = jdbcApplicationSettings.getAdUnitConfigById("non-existing");

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertFailure(exception -> {
            assertThat(exception).isInstanceOf(PreBidException.class).hasMessage("Not found");
            async.complete();
        }));
    }
}