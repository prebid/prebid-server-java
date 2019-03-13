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
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.vertx.jdbc.BasicJdbcClient;
import org.prebid.server.vertx.jdbc.JdbcClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(VertxUnitRunner.class)
public class JdbcApplicationSettingsTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private static final String JDBC_URL = "jdbc:h2:mem:test";

    private static final String selectQuery =
            "SELECT reqid, requestData, 'request' as dataType FROM stored_requests WHERE reqid IN (%REQUEST_ID_LIST%) "
                    + "UNION ALL "
                    + "SELECT impid, impData, 'imp' as dataType FROM stored_imps WHERE impid IN (%IMP_ID_LIST%)";

    private static final String selectUnionQuery =
            "SELECT reqid, requestData, 'request' as dataType FROM stored_requests WHERE reqid IN (%REQUEST_ID_LIST%) "
                    + "UNION ALL "
                    + "SELECT reqid, requestData, 'request' as dataType FROM stored_requests2 WHERE reqid IN (%REQUEST_ID_LIST%) "
                    + "UNION ALL "
                    + "SELECT impid, impData, 'imp' as dataType FROM stored_imps WHERE impid IN (%IMP_ID_LIST%) "
                    + "UNION ALL "
                    + "SELECT impid, impData, 'imp' as dataType FROM stored_imps2 WHERE impid IN (%IMP_ID_LIST%)";

    private static final String selectFromOneColumnTableQuery = "SELECT reqid FROM one_column_table WHERE reqid IN " +
            "(%REQUEST_ID_LIST%)";

    private static Connection connection;

    private Vertx vertx;
    @Mock
    private Metrics metrics;

    private Clock clock;

    private JdbcApplicationSettings jdbcApplicationSettings;

    private Timeout timeout;

    @BeforeClass
    public static void beforeClass() throws SQLException {
        connection = DriverManager.getConnection(JDBC_URL);
        connection.createStatement().execute("CREATE TABLE accounts_account (id SERIAL PRIMARY KEY, uuid varchar(40) " +
                "NOT NULL, price_granularity varchar(6), granularityMultiplier numeric(9,3), banner_cache_ttl INT, " +
                "video_cache_ttl INT, events_enabled BIT);");
        connection.createStatement().execute("CREATE TABLE s2sconfig_config (id SERIAL PRIMARY KEY, uuid varchar(40) " +
                "NOT NULL, config varchar(512));");
        connection.createStatement().execute("CREATE TABLE stored_requests (id SERIAL PRIMARY KEY, reqid varchar(40) "
                + "NOT NULL, requestData varchar(512));");
        connection.createStatement().execute("CREATE TABLE stored_requests2 (id SERIAL PRIMARY KEY, reqid varchar(40) "
                + "NOT NULL, requestData varchar(512));");
        connection.createStatement().execute("CREATE TABLE stored_imps (id SERIAL PRIMARY KEY, impid varchar(40) "
                + "NOT NULL, impData varchar(512));");
        connection.createStatement().execute("CREATE TABLE stored_imps2 (id SERIAL PRIMARY KEY, impid varchar(40) "
                + "NOT NULL, impData varchar(512));");
        connection.createStatement().execute("CREATE TABLE one_column_table (id SERIAL PRIMARY KEY, reqid varchar(40)"
                + " NOT NULL);");
        connection.createStatement().execute("insert into accounts_account " +
                "(uuid, price_granularity, banner_cache_ttl, video_cache_ttl, events_enabled)" +
                " values ('accountId','med', 100, 100, TRUE);");
        connection.createStatement().execute("insert into s2sconfig_config (uuid, config)" +
                " values ('adUnitConfigId', 'config');");
        connection.createStatement().execute("insert into stored_requests (reqid, requestData) values ('1','value1');");
        connection.createStatement().execute("insert into stored_requests (reqid, requestData) values ('2','value2');");
        connection.createStatement().execute(
                "insert into stored_requests2 (reqid, requestData) values ('3','value3');");
        connection.createStatement().execute("insert into stored_imps (impid, impData) values ('4','value4');");
        connection.createStatement().execute("insert into stored_imps (impid, impData) values ('5','value5');");
        connection.createStatement().execute("insert into stored_imps2 (impid, impData) values ('6','value6');");
        connection.createStatement().execute("insert into one_column_table (reqid) values ('3');");
    }

    @AfterClass
    public static void afterClass() throws SQLException {
        connection.close();
    }

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(5000L);
        jdbcApplicationSettings = new JdbcApplicationSettings(jdbcClient(), selectQuery, selectQuery);
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void getAccountByIdShouldReturnAccountWithIdAndPriceGranularity(TestContext context) {
        // when
        final Future<Account> future = jdbcApplicationSettings.getAccountById("accountId", timeout);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(account -> {
            assertThat(account).isEqualTo(Account.of("accountId", "med", 100, 100, true));
            async.complete();
        }));
    }

    @Test
    public void getAccountByIdShouldFailIfAccountNotFound(TestContext context) {
        // when
        final Future<Account> future = jdbcApplicationSettings.getAccountById("non-existing", timeout);

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
        final Future<String> future = jdbcApplicationSettings.getAdUnitConfigById("adUnitConfigId", timeout);

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
        final Future<String> future = jdbcApplicationSettings.getAdUnitConfigById("non-existing", timeout);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertFailure(exception -> {
            assertThat(exception).isInstanceOf(PreBidException.class).hasMessage("Not found");
            async.complete();
        }));
    }

    @Test
    public void getStoredDataShouldReturnExpectedResult(TestContext context) {
        // when
        final Future<StoredDataResult> future = jdbcApplicationSettings.getStoredData(
                new HashSet<>(asList("1", "2")), new HashSet<>(asList("4", "5")), timeout);

        // then
        final Async async = context.async();
        final Map<String, String> expectedRequests = new HashMap<>();
        expectedRequests.put("1", "value1");
        expectedRequests.put("2", "value2");
        final Map<String, String> expectedImps = new HashMap<>();
        expectedImps.put("4", "value4");
        expectedImps.put("5", "value5");
        future.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult)
                    .isEqualTo(StoredDataResult.of(expectedRequests, expectedImps, emptyList()));
            async.complete();
        }));
    }

    @Test
    public void getAmpStoredDataShouldReturnExpectedResult(TestContext context) {
        // when
        final Future<StoredDataResult> future = jdbcApplicationSettings.getAmpStoredData(
                new HashSet<>(asList("1", "2")), new HashSet<>(asList("3", "4")), timeout);

        // then
        final Async async = context.async();
        final Map<String, String> expectedRequests = new HashMap<>();
        expectedRequests.put("1", "value1");
        expectedRequests.put("2", "value2");
        future.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult)
                    .isEqualTo(StoredDataResult.of(expectedRequests, emptyMap(), emptyList()));
            async.complete();
        }));
    }

    @Test
    public void getStoredDataUnionSelectByIdShouldReturnStoredRequests(TestContext context) {
        // given
        jdbcApplicationSettings = new JdbcApplicationSettings(jdbcClient(), selectUnionQuery, selectUnionQuery);

        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredData(new HashSet<>(asList("1", "2", "3")),
                        new HashSet<>(asList("4", "5", "6")), timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            final Map<String, String> expectedRequests = new HashMap<>();
            expectedRequests.put("1", "value1");
            expectedRequests.put("2", "value2");
            expectedRequests.put("3", "value3");
            final Map<String, String> expectedImps = new HashMap<>();
            expectedImps.put("4", "value4");
            expectedImps.put("5", "value5");
            expectedImps.put("6", "value6");
            assertThat(storedRequestResult).isEqualTo(
                    StoredDataResult.of(expectedRequests, expectedImps, emptyList()));
            async.complete();
        }));
    }

    @Test
    public void getAmpStoredDataUnionSelectByIdShouldReturnStoredRequests(TestContext context) {
        // given
        jdbcApplicationSettings = new JdbcApplicationSettings(jdbcClient(), selectUnionQuery, selectUnionQuery);

        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getAmpStoredData(new HashSet<>(asList("1", "2", "3")),
                        new HashSet<>(asList("4", "5", "6")), timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            final Map<String, String> expectedRequests = new HashMap<>();
            expectedRequests.put("1", "value1");
            expectedRequests.put("2", "value2");
            expectedRequests.put("3", "value3");
            assertThat(storedRequestResult).isEqualTo(
                    StoredDataResult.of(expectedRequests, emptyMap(), emptyList()));
            async.complete();
        }));
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorIfNoStoredRequestFound(TestContext context) {
        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredData(new HashSet<>(asList("1", "3")), emptySet(), timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredDataResult.of(singletonMap("1", "value1"), emptyMap(),
                    singletonList("No stored request found for id: 3")));
            async.complete();
        }));
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorIfNoStoredImpFound(TestContext context) {
        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredData(emptySet(), new HashSet<>(asList("4", "6")), timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredDataResult.of(emptyMap(), singletonMap("4", "value4"),
                    singletonList("No stored imp found for id: 6")));
            async.complete();
        }));
    }

    @Test
    public void getAmpStoredDataShouldReturnResultWithErrorIfNoStoredRequestFound(TestContext context) {
        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getAmpStoredData(new HashSet<>(asList("1", "3")), emptySet(), timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredDataResult.of(singletonMap("1", "value1"), emptyMap(),
                    singletonList("No stored request found for id: 3")));
            async.complete();
        }));
    }

    @Test
    public void getStoredDataShouldReturnErrorIfResultContainsLessColumnsThanExpected(TestContext context) {
        // given
        jdbcApplicationSettings = new JdbcApplicationSettings(jdbcClient(), selectFromOneColumnTableQuery,
                selectFromOneColumnTableQuery);

        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredData(new HashSet<>(asList("1", "2", "3")), emptySet(), timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredDataResult.of(emptyMap(), emptyMap(),
                    singletonList("Result set column number is less than expected")));
            async.complete();
        }));
    }

    @Test
    public void getAmpStoredDataShouldReturnErrorIfResultContainsLessColumnsThanExpected(TestContext context) {
        // given
        jdbcApplicationSettings = new JdbcApplicationSettings(jdbcClient(), selectFromOneColumnTableQuery,
                selectFromOneColumnTableQuery);

        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getAmpStoredData(new HashSet<>(asList("1", "2", "3")), emptySet(), timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredDataResult.of(emptyMap(), emptyMap(),
                    singletonList("Result set column number is less than expected")));
            async.complete();
        }));
    }

    @Test
    public void getStoredDataShouldReturnErrorAndEmptyResult(TestContext context) {
        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredData(new HashSet<>(asList("3", "4")),
                        new HashSet<>(asList("6", "7")), timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredDataResult.of(emptyMap(), emptyMap(),
                    singletonList("No stored requests for ids [3, 4] and stored imps for ids [6, 7] was found")));
            async.complete();
        }));
    }

    @Test
    public void getAmpStoredDataShouldReturnErrorAndEmptyResult(TestContext context) {
        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getAmpStoredData(new HashSet<>(asList("3", "4")), emptySet(), timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredDataResult.of(emptyMap(), emptyMap(),
                    singletonList("No stored requests for ids [3, 4] was found")));
            async.complete();
        }));
    }

    @Test
    public void getAmpStoredDataShouldIgnoreImpIdsArgument(TestContext context) {
        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getAmpStoredData(singleton("1"), singleton("4"), timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredDataResult.of(singletonMap("1", "value1"), emptyMap(),
                    emptyList()));
            async.complete();
        }));
    }

    private JdbcClient jdbcClient() {
        return new BasicJdbcClient(vertx, JDBCClient.createShared(vertx,
                new JsonObject()
                        .put("url", JDBC_URL)
                        .put("driver_class", "org.h2.Driver")
                        .put("max_pool_size", 10)), metrics, clock
        );
    }
}
