package org.prebid.server.settings;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.settings.mapper.JdbcStoredDataResultMapper;
import org.prebid.server.settings.mapper.JdbcStoredResponseResultMapper;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

@SuppressWarnings("ALL")
@RunWith(VertxUnitRunner.class)
public class JdbcApplicationSettingsTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private static final String JDBC_USERNAME = "sa";
    private static final String JDBC_PASSWORD = "";
    private static final String JDBC_SCHEMA = "test";
    private static final String JDBC_URL = "jdbc:h2:mem:" + JDBC_SCHEMA;

    private static final String REQUEST_ID_PLACEHOLDER = "%REQUEST_ID_LIST%";
    private static final String IMP_ID_PLACEHOLDER = "%IMP_ID_LIST%";
    private static final String RESPONSE_ID_PLACEHOLDER = "%RESPONSE_ID_LIST%";

    private static final String SELECT_QUERY =
            "SELECT reqid, requestData, 'request' as dataType FROM stored_requests WHERE reqid IN (%REQUEST_ID_LIST%) "
                    + "UNION ALL "
                    + "SELECT impid, impData, 'imp' as dataType FROM stored_imps WHERE impid IN (%IMP_ID_LIST%)";

    private static final String SELECT_AMP_QUERY =
            "SELECT reqid, requestData, 'request' as dataType FROM stored_requests WHERE reqid IN (%REQUEST_ID_LIST%)";

    private static final String SELECT_UNION_QUERY =
            "SELECT reqid, requestData, 'request' as dataType FROM stored_requests WHERE reqid IN (%REQUEST_ID_LIST%) "
                    + "UNION ALL "
                    + "SELECT reqid, requestData, 'request' as dataType FROM stored_requests2 "
                    + "WHERE reqid IN (%REQUEST_ID_LIST%) "
                    + "UNION ALL "
                    + "SELECT impid, impData, 'imp' as dataType FROM stored_imps WHERE impid IN (%IMP_ID_LIST%) "
                    + "UNION ALL "
                    + "SELECT impid, impData, 'imp' as dataType FROM stored_imps2 WHERE impid IN (%IMP_ID_LIST%)";

    private static final String SELECT_FROM_ONE_COLUMN_TABLE_QUERY =
            "SELECT reqid FROM one_column_table WHERE reqid IN "
                    + "(%REQUEST_ID_LIST%)";

    private static final String SELECT_RESPONSE_QUERY = "SELECT responseId, responseData FROM stored_responses"
            + " WHERE responseId IN (%RESPONSE_ID_LIST%)";

    private static final String SELECT_ONE_COLUMN_RESPONSE_QUERY = "SELECT responseId FROM stored_responses"
            + " WHERE responseId IN (%RESPONSE_ID_LIST%)";

    private static final String SELECT_ACCOUNTS_QUERY =
            "SELECT uuid, price_granularity, banner_cache_ttl, video_cache_ttl,"
            + " events_enabled, enforce_ccpa, tcf_config, analytics_sampling_factor, truncate_target_attr"
            + " FROM accounts_account where uuid = ? LIMIT 1";

    private static final String SELECT_ADUNIT_QUERY = "SELECT config FROM s2sconfig_config where uuid = ? LIMIT 1";

    private static Connection connection;

    private Vertx vertx;

    private static EmbeddedDatabase db;

    private static JDBCClient client;

    @BeforeClass
    public static void beforeClass() throws SQLException {
        db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .build();

        Properties info = new Properties();
        info.setProperty("username", JDBC_USERNAME);
        info.setProperty("password", JDBC_PASSWORD);
        connection = DriverManager.getConnection(JDBC_URL, info);
        connection.createStatement().execute("CREATE TABLE accounts_account (id SERIAL PRIMARY KEY, "
                + "uuid varchar(40) NOT NULL, price_granularity varchar(6), granularityMultiplier numeric(9,3), "
                + "banner_cache_ttl INT, video_cache_ttl INT, events_enabled BIT, enforce_ccpa BIT, "
                + "tcf_config varchar(512), analytics_sampling_factor INT, truncate_target_attr INT);");
        connection.createStatement().execute(
                "CREATE TABLE s2sconfig_config (id SERIAL PRIMARY KEY, uuid varchar(40) "
                + "NOT NULL, config varchar(512));");
        connection.createStatement().execute(
                "CREATE TABLE stored_requests (id SERIAL PRIMARY KEY, reqid varchar(40) "
                + "NOT NULL, requestData varchar(512));");
        connection.createStatement().execute(
                "CREATE TABLE stored_requests2 (id SERIAL PRIMARY KEY, reqid varchar(40) "
                + "NOT NULL, requestData varchar(512));");
        connection.createStatement().execute(
                "CREATE TABLE stored_imps (id SERIAL PRIMARY KEY, impid varchar(40) "
                + "NOT NULL, impData varchar(512));");
        connection.createStatement().execute(
                "CREATE TABLE stored_imps2 (id SERIAL PRIMARY KEY, impid varchar(40) "
                + "NOT NULL, impData varchar(512));");
        connection.createStatement().execute(
                "CREATE TABLE stored_responses (id SERIAL PRIMARY KEY, responseId varchar(40) NOT NULL,"
                        + " responseData varchar(512));");
        connection.createStatement().execute(
                "CREATE TABLE one_column_table (id SERIAL PRIMARY KEY, reqid varchar(40)"
                + " NOT NULL);");
        connection.createStatement().execute("insert into accounts_account "
                + "(uuid, price_granularity, banner_cache_ttl, video_cache_ttl, events_enabled, enforce_ccpa, "
                + "tcf_config, analytics_sampling_factor, truncate_target_attr) "
                + "values ('accountId','med', 100, 100, TRUE, TRUE, '{\"enabled\": true}', 1, 0);");
        connection.createStatement().execute("insert into s2sconfig_config (uuid, config)"
                + " values ('adUnitConfigId', 'config');");
        connection.createStatement().execute(
                "insert into stored_requests (reqid, requestData) values ('1','value1');");
        connection.createStatement().execute(
                "insert into stored_requests (reqid, requestData) values ('2','value2');");
        connection.createStatement().execute(
                "insert into stored_requests2 (reqid, requestData) values ('3','value3');");
        connection.createStatement().execute("insert into stored_imps (impid, impData) values ('4','value4');");
        connection.createStatement().execute("insert into stored_imps (impid, impData) values ('5','value5');");
        connection.createStatement().execute("insert into stored_imps2 (impid, impData) values ('6','value6');");
        connection.createStatement().execute("insert into stored_responses (responseId, responseData) "
                + "values ('1','response1');");
        connection.createStatement().execute("insert into stored_responses (responseId, responseData) "
                + "values ('2','response2');");
        connection.createStatement().execute("insert into one_column_table (reqid) values ('3');");
    }

    @AfterClass
    public static void afterClass() throws SQLException {
        connection.close();
        db.shutdown();
    }

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
        JsonObject configs = new JsonObject()
                .put("url", JDBC_URL)
                .put("driver_class", "org.h2.Driver")
                .put("max_pool_size", 10)
                .put("username", JDBC_USERNAME)
                .put("password", JDBC_PASSWORD);
        client = JDBCClient.createShared(vertx, configs);
    }

    @After
    public void tearDown() {
        vertx.close();
    }

    @Test
    public void getAccountByIdShouldReturnAccountWithAllFieldsPopulated(TestContext context) {
        final Async async = context.async();
        client.queryWithParams(SELECT_ACCOUNTS_QUERY,
                new JsonArray().add("accountId"),
                result -> {
                    JsonObject json = result.result().getRows().get(0);
                    Account account = Account.builder()
                            .id(json.getString("UUID"))
                            .priceGranularity(json.getString("PRICE_GRANULARITY"))
                            .bannerCacheTtl(json.getInteger("BANNER_CACHE_TTL"))
                            .videoCacheTtl(json.getInteger("VIDEO_CACHE_TTL"))
                            .analyticsSamplingFactor(json.getInteger("ANALYTICS_SAMPLING_FACTOR"))
                            .eventsEnabled(json.getBoolean("EVENTS_ENABLED"))
                            .gdpr(toAccountTcfConfig(json.getString("TCF_CONFIG"))).build();

                    assertThat(account).isEqualTo(Account.builder()
                            .id("accountId")
                            .priceGranularity("med")
                            .bannerCacheTtl(100)
                            .videoCacheTtl(100)
                            .analyticsSamplingFactor(1)
                            .eventsEnabled(true)
                            .gdpr(AccountGdprConfig.builder()
                                    .enabled(true)
                                    .build())
                            .build());

                    async.complete();
                });
    }

    @Test
    public void getAccountByIdShouldFailIfAccountNotFound(TestContext context) {
        final Async async = context.async();
        client.queryWithParams(SELECT_ACCOUNTS_QUERY,
                new JsonArray().add("non-existing"),
                result -> {
                    if (result.succeeded()) {
                        // result set should be 0
                        assertThat(result.result().getResults().size()).isEqualTo(0);
                    } else {
                        fail("SQL query failed.");
                    }
                    async.complete();
                });
    }

    @Test
    public void getAdUnitConfigByIdShouldReturnConfig(TestContext context) {
        final Async async = context.async();
        client.queryWithParams(SELECT_ADUNIT_QUERY,
                new JsonArray().add("adUnitConfigId"),
                result -> {
                    if (result.succeeded()) {
                        JsonObject json = result.result().getRows().get(0);
                        assertThat(json.getString("CONFIG")).isEqualTo("config");
                    } else {
                        fail("SQL query failed.");
                    }
                    async.complete();
                });
    }

    @Test
    public void getAdUnitConfigByIdShouldFailIfConfigNotFound(TestContext context) {
        final Async async = context.async();
        client.queryWithParams(SELECT_ADUNIT_QUERY,
                new JsonArray().add("non-existing"),
                result -> {
                    if (result.succeeded()) {
                        // result set should be 0
                        assertThat(result.result().getResults().size()).isEqualTo(0);
                    } else {
                        fail("SQL query failed.");
                    }
                    async.complete();
                });
    }

    @Test
    public void getStoredDataShouldReturnExpectedResult(TestContext context) {
        Set<String> requestIds = new HashSet<>(asList("1", "2"));
        Set<String> impIds = new HashSet<>(asList("4", "5"));
        final Map<String, String> expectedRequests = new HashMap<>();
        expectedRequests.put("1", "value1");
        expectedRequests.put("2", "value2");
        final Map<String, String> expectedImps = new HashMap<>();
        expectedImps.put("4", "value4");
        expectedImps.put("5", "value5");

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredDataResult storedDataResult = JdbcStoredDataResultMapper.map(result.result(), requestIds, impIds);
                assertThat(storedDataResult)
                        .isEqualTo(StoredDataResult.of(expectedRequests, expectedImps, emptyList()));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredData(SELECT_QUERY, requestIds, impIds, handler);
    }

    @Test
    public void getAmpStoredDataShouldReturnExpectedResult(TestContext context) {
        Set<String> requestIds = new HashSet<>(asList("1", "2"));
        Set<String> impIds = new HashSet<>(asList("3", "4"));
        final Map<String, String> expectedRequests = new HashMap<>();
        expectedRequests.put("1", "value1");
        expectedRequests.put("2", "value2");

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredDataResult storedDataResult = JdbcStoredDataResultMapper.map(result.result(), requestIds, impIds);
                assertThat(storedDataResult)
                        .isEqualTo(StoredDataResult.of(
                                expectedRequests,
                                emptyMap(),
                                asList("No stored imp found for id: 3", "No stored imp found for id: 4")));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredData(SELECT_AMP_QUERY, requestIds, impIds, handler);
    }

    @Test
    public void getVideoStoredDataShouldReturnExpectedResult(TestContext context) {
        Set<String> requestIds = new HashSet<>(asList("1", "2"));
        Set<String> impIds = new HashSet<>(asList("4", "5"));
        final Map<String, String> expectedRequests = new HashMap<>();
        expectedRequests.put("1", "value1");
        expectedRequests.put("2", "value2");
        final Map<String, String> expectedImps = new HashMap<>();
        expectedImps.put("4", "value4");
        expectedImps.put("5", "value5");

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredDataResult storedDataResult = JdbcStoredDataResultMapper.map(result.result(), requestIds, impIds);
                assertThat(storedDataResult)
                        .isEqualTo(StoredDataResult.of(expectedRequests, expectedImps, emptyList()));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredData(SELECT_QUERY, requestIds, impIds, handler);
    }

    @Test
    public void getVideoStoredDataShouldReturnStoredRequests(TestContext context) {
        Set<String> requestIds = new HashSet<>(asList("1", "2", "3"));
        Set<String> impIds = new HashSet<>(asList("4", "5", "6"));
        final Map<String, String> expectedRequests = new HashMap<>();
        expectedRequests.put("1", "value1");
        expectedRequests.put("2", "value2");
        expectedRequests.put("3", "value3");
        final Map<String, String> expectedImps = new HashMap<>();
        expectedImps.put("4", "value4");
        expectedImps.put("5", "value5");
        expectedImps.put("6", "value6");

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredDataResult storedDataResult = JdbcStoredDataResultMapper.map(result.result(), requestIds, impIds);
                assertThat(storedDataResult)
                        .isEqualTo(StoredDataResult.of(expectedRequests, expectedImps, emptyList()));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredData(SELECT_UNION_QUERY, requestIds, impIds, handler);
    }

    @Test
    public void getStoredDataUnionSelectByIdShouldReturnStoredRequests(TestContext context) {
        Set<String> requestIds = new HashSet<>(asList("1", "2", "3"));
        Set<String> impIds = new HashSet<>(asList("4", "5", "6"));
        final Map<String, String> expectedRequests = new HashMap<>();
        expectedRequests.put("1", "value1");
        expectedRequests.put("2", "value2");
        expectedRequests.put("3", "value3");
        final Map<String, String> expectedImps = new HashMap<>();
        expectedImps.put("4", "value4");
        expectedImps.put("5", "value5");
        expectedImps.put("6", "value6");

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredDataResult storedDataResult = JdbcStoredDataResultMapper.map(result.result(), requestIds, impIds);
                assertThat(storedDataResult)
                        .isEqualTo(StoredDataResult.of(expectedRequests, expectedImps, emptyList()));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredData(SELECT_UNION_QUERY, requestIds, impIds, handler);
    }

    @Test
    public void getAmpStoredDataUnionSelectByIdShouldReturnStoredRequests(TestContext context) {
        Set<String> requestIds = new HashSet<>(asList("1", "2", "3"));
        Set<String> impIds = new HashSet<>(asList("4", "5", "6"));
        final Map<String, String> expectedRequests = new HashMap<>();
        expectedRequests.put("1", "value1");
        expectedRequests.put("2", "value2");
        expectedRequests.put("3", "value3");
        final Map<String, String> expectedImps = new HashMap<>();
        expectedImps.put("4", "value4");
        expectedImps.put("5", "value5");
        expectedImps.put("6", "value6");

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredDataResult storedDataResult = JdbcStoredDataResultMapper.map(result.result(), requestIds, impIds);
                assertThat(storedDataResult)
                        .isEqualTo(StoredDataResult.of(expectedRequests, expectedImps, emptyList()));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredData(SELECT_UNION_QUERY, requestIds, impIds, handler);
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorIfNoStoredRequestFound(TestContext context) {
        Set<String> requestIds = new HashSet<>(asList("1", "3"));
        final Map<String, String> expectedRequests = singletonMap("1", "value1");

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredDataResult storedDataResult = JdbcStoredDataResultMapper.map(
                        result.result(),
                        requestIds,
                        emptySet());
                assertThat(storedDataResult)
                        .isEqualTo(StoredDataResult.of(expectedRequests,
                                emptyMap(), singletonList("No stored request found for id: 3")));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredData(SELECT_QUERY, requestIds, emptySet(), handler);
    }

    @Test
    public void getStoredDataShouldReturnResultWithErrorIfNoStoredImpFound(TestContext context) {
        Set<String> impIds = new HashSet<>(asList("4", "6"));
        final Map<String, String> expectedImps = singletonMap("4", "value4");

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredDataResult storedDataResult = JdbcStoredDataResultMapper.map(result.result(), emptySet(), impIds);
                assertThat(storedDataResult)
                        .isEqualTo(StoredDataResult.of(emptyMap(),
                                expectedImps, singletonList("No stored imp found for id: 6")));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredData(SELECT_QUERY, emptySet(), impIds, handler);
    }

    @Test
    public void getAmpStoredDataShouldReturnResultWithErrorIfNoStoredRequestFound(TestContext context) {
        Set<String> requestIds = new HashSet<>(asList("1", "3"));
        final Map<String, String> expectedRequests = singletonMap("1", "value1");

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredDataResult storedDataResult = JdbcStoredDataResultMapper.map(
                        result.result(),
                        requestIds,
                        emptySet());
                assertThat(storedDataResult)
                        .isEqualTo(StoredDataResult.of(expectedRequests,
                                emptyMap(), singletonList("No stored request found for id: 3")));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredData(SELECT_QUERY, requestIds, emptySet(), handler);
    }

    @Test
    public void getStoredDataShouldReturnErrorIfResultContainsLessColumnsThanExpected(TestContext context) {
        Set<String> requestIds = new HashSet<>(asList("1", "2", "3"));

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredDataResult storedDataResult = JdbcStoredDataResultMapper.map(
                        result.result(),
                        requestIds,
                        emptySet());
                assertThat(storedDataResult)
                        .isEqualTo(StoredDataResult.of(emptyMap(),
                                emptyMap(), singletonList("Result set column number is less than expected")));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredData(SELECT_FROM_ONE_COLUMN_TABLE_QUERY, requestIds, emptySet(), handler);
    }

    @Test
    public void getAmpStoredDataShouldReturnErrorIfResultContainsLessColumnsThanExpected(TestContext context) {
        Set<String> requestIds = new HashSet<>(asList("1", "2", "3"));

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredDataResult storedDataResult = JdbcStoredDataResultMapper.map(
                        result.result(),
                        requestIds,
                        emptySet());
                assertThat(storedDataResult)
                        .isEqualTo(StoredDataResult.of(emptyMap(),
                                emptyMap(), singletonList("Result set column number is less than expected")));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredData(SELECT_FROM_ONE_COLUMN_TABLE_QUERY, requestIds, emptySet(), handler);
    }

    @Test
    public void getStoredDataShouldReturnErrorAndEmptyResult(TestContext context) {
        Set<String> requestIds = new HashSet<>(asList("3", "4"));
        Set<String> impIds = new HashSet<>(asList("6", "7"));

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredDataResult storedDataResult = JdbcStoredDataResultMapper.map(result.result(), requestIds, impIds);
                assertThat(storedDataResult)
                        .isEqualTo(StoredDataResult.of(
                                emptyMap(),
                                emptyMap(),
                                singletonList(
                                        "No stored requests for ids [3, 4] and stored imps for ids [6, 7] were found"
                                )));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredData(SELECT_QUERY, requestIds, impIds, handler);
    }

    @Test
    public void getAmpStoredDataShouldReturnErrorAndEmptyResult(TestContext context) {
        Set<String> requestIds = new HashSet<>(asList("3", "4"));

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredDataResult storedDataResult = JdbcStoredDataResultMapper.map(
                        result.result(),
                        requestIds,
                        emptySet());
                assertThat(storedDataResult)
                        .isEqualTo(StoredDataResult.of(
                                emptyMap(),
                                emptyMap(),
                                singletonList("No stored requests for ids [3, 4] were found")));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredData(SELECT_AMP_QUERY, requestIds, emptySet(), handler);
    }

    @Test
    public void getAmpStoredDataShouldIgnoreImpIdsArgument(TestContext context) {
        Set<String> requestIds = singleton("1");
        Set<String> impIds = singleton("4");
        final Map<String, String> expectedRequests = singletonMap("1", "value1");

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredDataResult storedDataResult = JdbcStoredDataResultMapper.map(result.result(), requestIds, impIds);
                assertThat(storedDataResult)
                        .isEqualTo(StoredDataResult.of(
                                expectedRequests,
                                emptyMap(),
                                singletonList("No stored imp found for id: 4")));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredData(SELECT_AMP_QUERY, requestIds, impIds, handler);
    }

    @Test
    public void getStoredResponseShouldReturnExpectedResult(TestContext context) {
        Set<String> responseIds = new HashSet<>(asList("1", "2"));
        final Map<String, String> expectedResponses = new HashMap<>();
        expectedResponses.put("1", "response1");
        expectedResponses.put("2", "response2");

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredResponseDataResult storedDataResult = JdbcStoredResponseResultMapper.map(
                        result.result(),
                        responseIds);
                assertThat(storedDataResult)
                        .isEqualTo(StoredResponseDataResult.of(
                                expectedResponses,
                                emptyList()));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredResponses(SELECT_RESPONSE_QUERY, responseIds, handler);
    }

    @Test
    public void getStoredResponseShouldReturnResultWithErrorIfNotAllStoredResponsesWereFound(TestContext context) {
        Set<String> responseIds = new HashSet<>(asList("1", "3"));
        final Map<String, String> expectedResponses = new HashMap<>();
        expectedResponses.put("1", "response1");

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredResponseDataResult storedDataResult = JdbcStoredResponseResultMapper.map(
                        result.result(),
                        responseIds);
                assertThat(storedDataResult)
                        .isEqualTo(StoredResponseDataResult.of(
                                expectedResponses,
                                singletonList("No stored response found for id: 3")));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredResponses(SELECT_RESPONSE_QUERY, responseIds, handler);
    }

    @Test
    public void getStoredResponseShouldReturnErrorIfResultContainsLessColumnsThanExpected(TestContext context) {
        Set<String> responseIds = new HashSet<>(asList("1", "2", "3"));

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredResponseDataResult storedDataResult = JdbcStoredResponseResultMapper.map(
                        result.result(),
                        responseIds);
                assertThat(storedDataResult)
                        .isEqualTo(StoredResponseDataResult.of(
                                emptyMap(),
                                singletonList("Result set column number is less than expected")));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredResponses(SELECT_ONE_COLUMN_RESPONSE_QUERY, responseIds, handler);
    }

    @Test
    public void getStoredResponseShouldReturnErrorAndEmptyResult(TestContext context) {
        Set<String> responseIds = new HashSet<>(asList("3", "4"));

        final Async async = context.async();
        Handler<AsyncResult<ResultSet>> handler = result -> {
            if (result.succeeded()) {
                StoredResponseDataResult storedDataResult = JdbcStoredResponseResultMapper.map(
                        result.result(),
                        responseIds);
                assertThat(storedDataResult)
                        .isEqualTo(StoredResponseDataResult.of(
                                emptyMap(),
                                singletonList("No stored responses were found for ids: 3,4")));
            } else {
                fail("SQL query failed.");
            }
            async.complete();
        };
        validateStoredResponses(SELECT_RESPONSE_QUERY, responseIds, handler);
    }

    private AccountGdprConfig toAccountTcfConfig(String tcfConfig) {
        try {
            return tcfConfig != null ? jacksonMapper.decodeValue(tcfConfig, AccountGdprConfig.class) : null;
        } catch (DecodeException e) {
            throw new PreBidException(e.getMessage());
        }
    }

    /**
     * Creates parametrized query from query and variable templates, by replacing templateVariable
     * with appropriate number of "?" placeholders.
     */
    private static String createParametrizedQuery(String query, int requestIdsSize, int impIdsSize) {
        return query
                .replace(REQUEST_ID_PLACEHOLDER, parameterHolders(requestIdsSize))
                .replace(IMP_ID_PLACEHOLDER, parameterHolders(impIdsSize));
    }

    /**
     * Returns string for parametrized placeholder.
     */
    private static String parameterHolders(int paramsSize) {
        return paramsSize == 0
                ? "NULL"
                : IntStream.range(0, paramsSize).mapToObj(i -> "?").collect(Collectors.joining(","));
    }

    /**
     * Fetches stored requests from database for the given query.
     */
    private void validateStoredData(String query, Set<String> requestIds, Set<String> impIds,
                                    Handler<AsyncResult<ResultSet>> handler) {
        final List<Object> idsQueryParameters = new ArrayList<>();
        IntStream.rangeClosed(1, StringUtils.countMatches(query, REQUEST_ID_PLACEHOLDER))
                .forEach(i -> idsQueryParameters.addAll(requestIds));
        IntStream.rangeClosed(1, StringUtils.countMatches(query, IMP_ID_PLACEHOLDER))
                .forEach(i -> idsQueryParameters.addAll(impIds));

        final String parametrizedQuery = createParametrizedQuery(query, requestIds.size(), impIds.size());

        client.queryWithParams(parametrizedQuery, new JsonArray(idsQueryParameters), handler);
    }

    /**
     * Runs a process to get stored responses by a collection of ids from database.
     */
    public void validateStoredResponses(String query,
                                        Set<String> responseIds,
                                        Handler<AsyncResult<ResultSet>> handler) {
        final String queryResolvedWithParameters = query.replaceAll(RESPONSE_ID_PLACEHOLDER,
                parameterHolders(responseIds.size()));

        final List<Object> idsQueryParameters = new ArrayList<>();
        IntStream.rangeClosed(1, StringUtils.countMatches(query, RESPONSE_ID_PLACEHOLDER))
                .forEach(i -> idsQueryParameters.addAll(responseIds));

        client.queryWithParams(queryResolvedWithParameters, new JsonArray(idsQueryParameters), handler);
    }
}
