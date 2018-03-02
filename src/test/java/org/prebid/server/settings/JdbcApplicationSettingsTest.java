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
import org.prebid.server.settings.model.StoredRequestResult;
import org.prebid.server.vertx.JdbcClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(VertxUnitRunner.class)
public class JdbcApplicationSettingsTest {

    private static final String JDBC_URL = "jdbc:h2:mem:test";
    private static final String selectQuery = "SELECT reqid, requestData FROM stored_requests WHERE reqid IN " +
            "(%ID_LIST%)";
    private static final String selectUnionQuery = "SELECT reqid, requestData FROM stored_requests where reqid IN " +
            "(%ID_LIST%) UNION SELECT reqid, requestData FROM stored_requests2 where reqid IN (%ID_LIST%)";
    private static final String selectFromOneColumnTableQuery = "SELECT reqid FROM one_column_table WHERE reqid IN " +
            "(%ID_LIST%)";

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
        connection.createStatement().execute("CREATE TABLE stored_requests (id SERIAL PRIMARY KEY, reqid varchar(40) "
                + "NOT NULL, requestData varchar(512));");
        connection.createStatement().execute("CREATE TABLE stored_requests2 (id SERIAL PRIMARY KEY, reqid varchar(40) "
                + "NOT NULL, requestData varchar(512));");
        connection.createStatement().execute("CREATE TABLE one_column_table (id SERIAL PRIMARY KEY, reqid varchar(40)"
                + " NOT NULL);");
        connection.createStatement().execute("insert into accounts_account (uuid, price_granularity)" +
                " values ('accountId','med');");
        connection.createStatement().execute("insert into s2sconfig_config (uuid, config)" +
                " values ('adUnitConfigId', 'config');");
        connection.createStatement().execute("insert into stored_requests (reqid, requestData) values ('1','value1');");
        connection.createStatement().execute("insert into stored_requests (reqid, requestData) values ('2','value2');");
        connection.createStatement().execute("insert into stored_requests2 (reqid, requestData)" +
                " values ('3','value3');");
        connection.createStatement().execute("insert into one_column_table (reqid) values ('3');");
    }

    @AfterClass
    public static void afterClass() throws SQLException {
        connection.close();
    }

    @Before
    public void setUp() {
        vertx = Vertx.vertx();

        this.jdbcApplicationSettings = new JdbcApplicationSettings(jdbcClient(), selectQuery, selectQuery);
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
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

    @Test
    public void getStoredRequestsByIdShouldReturnStoredRequests(TestContext context) {
        // when
        final Future<StoredRequestResult> future =
                jdbcApplicationSettings.getStoredRequestsById(new HashSet<>(asList("1", "2")), timeout());

        // then
        final Async async = context.async();
        final Map<String, String> expectedResultMap = new HashMap<>();
        expectedResultMap.put("1", "value1");
        expectedResultMap.put("2", "value2");
        future.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredRequestResult
                    .of(expectedResultMap, emptyList()));
            async.complete();
        }));
    }

    @Test
    public void getStoredRequestsByAmpIdShouldReturnStoredRequests(TestContext context) {
        // when
        final Future<StoredRequestResult> future =
                jdbcApplicationSettings.getStoredRequestsByAmpId(new HashSet<>(asList("1", "2")), timeout());

        // then
        final Async async = context.async();
        final Map<String, String> expectedResultMap = new HashMap<>();
        expectedResultMap.put("1", "value1");
        expectedResultMap.put("2", "value2");
        future.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredRequestResult
                    .of(expectedResultMap, emptyList()));
            async.complete();
        }));
    }

    @Test
    public void getStoredRequestsUnionSelectByIdShouldReturnStoredRequests(TestContext context) {
        // given
        jdbcApplicationSettings = new JdbcApplicationSettings(jdbcClient(), selectUnionQuery, selectUnionQuery);

        // when
        final Future<StoredRequestResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredRequestsById(new HashSet<>(asList("1", "2", "3")), timeout());

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            final Map<String, String> expectedResultMap = new HashMap<>();
            expectedResultMap.put("1", "value1");
            expectedResultMap.put("2", "value2");
            expectedResultMap.put("3", "value3");
            assertThat(storedRequestResult).isEqualTo(
                    StoredRequestResult.of(expectedResultMap, emptyList()));
            async.complete();
        }));
    }

    @Test
    public void getStoredRequestsUnionSelectByAmpIdShouldReturnStoredRequests(TestContext context) {
        // given
        jdbcApplicationSettings = new JdbcApplicationSettings(jdbcClient(), selectUnionQuery, selectUnionQuery);

        // when
        final Future<StoredRequestResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredRequestsByAmpId(new HashSet<>(asList("1", "2", "3")), timeout());

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            final Map<String, String> expectedResultMap = new HashMap<>();
            expectedResultMap.put("1", "value1");
            expectedResultMap.put("2", "value2");
            expectedResultMap.put("3", "value3");
            assertThat(storedRequestResult).isEqualTo(
                    StoredRequestResult.of(expectedResultMap, emptyList()));
            async.complete();
        }));
    }

    @Test
    public void getStoredRequestsByIdShouldReturnStoredRequestsWithError(TestContext context) {
        // when
        final Future<StoredRequestResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredRequestsById(new HashSet<>(asList("1", "3")), timeout());

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredRequestResult.of(singletonMap("1", "value1"),
                    singletonList("No config found for id: 3")));
            async.complete();
        }));
    }

    @Test
    public void getStoredRequestsByAmpIdShouldReturnStoredRequestsWithError(TestContext context) {
        // when
        final Future<StoredRequestResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredRequestsByAmpId(new HashSet<>(asList("1", "3")), timeout());

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredRequestResult.of(singletonMap("1", "value1"),
                    singletonList("No config found for id: 3")));
            async.complete();
        }));
    }

    @Test
    public void getStoredRequestByIdShouldReturnErrorIfResultContainsLessColumnsThanExpected(TestContext context) {
        // given
        jdbcApplicationSettings = new JdbcApplicationSettings(jdbcClient(), selectFromOneColumnTableQuery,
                selectFromOneColumnTableQuery);

        // when
        final Future<StoredRequestResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredRequestsById(new HashSet<>(asList("1", "2", "3")), timeout());

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredRequestResult.of(emptyMap(),
                    singletonList("Result set column number is less than expected")));
            async.complete();
        }));
    }

    @Test
    public void getStoredRequestByAmpIdShouldReturnErrorIfResultContainsLessColumnsThanExpected(TestContext context) {
        // given
        jdbcApplicationSettings = new JdbcApplicationSettings(jdbcClient(), selectFromOneColumnTableQuery,
                selectFromOneColumnTableQuery);

        // when
        final Future<StoredRequestResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredRequestsByAmpId(new HashSet<>(asList("1", "2", "3")), timeout());

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredRequestResult.of(emptyMap(),
                    singletonList("Result set column number is less than expected")));
            async.complete();
        }));
    }

    @Test
    public void getStoredRequestsByIdShouldReturnErrorAndEmptyResult(TestContext context) {
        // when
        final Future<StoredRequestResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredRequestsById(new HashSet<>(asList("3", "4")), timeout());

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredRequestResult.of(emptyMap(),
                    singletonList("Stored requests for ids [3, 4] was not found")));
            async.complete();
        }));
    }

    @Test
    public void getStoredRequestsByAmpIdShouldReturnErrorAndEmptyResult(TestContext context) {
        // when
        final Future<StoredRequestResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredRequestsByAmpId(new HashSet<>(asList("3", "4")), timeout());

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredRequestResult.of(emptyMap(),
                    singletonList("Stored requests for ids [3, 4] was not found")));
            async.complete();
        }));
    }

    private JdbcClient jdbcClient() {
        return new JdbcClient(vertx, JDBCClient.createShared(vertx,
                new JsonObject()
                        .put("url", JDBC_URL)
                        .put("driver_class", "org.h2.Driver")
                        .put("max_pool_size", 10)));
    }

    private static GlobalTimeout timeout() {
        return GlobalTimeout.create(1000);
    }
}
