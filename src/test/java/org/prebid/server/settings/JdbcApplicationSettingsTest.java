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
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountBidValidationConfig;
import org.prebid.server.settings.model.AccountCookieSyncConfig;
import org.prebid.server.settings.model.AccountEventsConfig;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.AccountStatus;
import org.prebid.server.settings.model.BidValidationEnforcement;
import org.prebid.server.settings.model.EnabledForRequestType;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;
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
public class JdbcApplicationSettingsTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private static final String JDBC_URL = "jdbc:h2:mem:test";

    private static final String SELECT_ACCOUNT_QUERY =
            "SELECT config FROM accounts_account where uuid = %ACCOUNT_ID% LIMIT 1";

    private static final String SELECT_QUERY =
            "SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests "
                    + "WHERE reqid IN (%REQUEST_ID_LIST%) "
                    + "UNION ALL "
                    + "SELECT accountId, impid, impData, 'imp' as dataType FROM stored_imps "
                    + "WHERE impid IN (%IMP_ID_LIST%)";

    private static final String SELECT_UNION_QUERY =
            "SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests "
                    + "WHERE reqid IN (%REQUEST_ID_LIST%) "
                    + "UNION ALL "
                    + "SELECT accountId, reqid, requestData, 'request' as dataType FROM stored_requests2 "
                    + "WHERE reqid IN (%REQUEST_ID_LIST%) "
                    + "UNION ALL "
                    + "SELECT accountId, impid, impData, 'imp' as dataType FROM stored_imps "
                    + "WHERE impid IN (%IMP_ID_LIST%) "
                    + "UNION ALL "
                    + "SELECT accountId, impid, impData, 'imp' as dataType FROM stored_imps2 "
                    + "WHERE impid IN (%IMP_ID_LIST%)";

    private static final String SELECT_FROM_ONE_COLUMN_TABLE_QUERY = "SELECT reqid FROM one_column_table "
            + "WHERE reqid IN (%REQUEST_ID_LIST%)";

    private static final String SELECT_RESPONSE_QUERY = "SELECT responseId, responseData FROM stored_responses "
            + "WHERE responseId IN (%RESPONSE_ID_LIST%)";

    private static final String SELECT_ONE_COLUMN_RESPONSE_QUERY = "SELECT responseId FROM stored_responses "
            + "WHERE responseId IN (%RESPONSE_ID_LIST%)";

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
        connection.createStatement().execute(
                "CREATE TABLE accounts_account ("
                        + "id SERIAL PRIMARY KEY, "
                        + "uuid varchar(40) NOT NULL, "
                        + "config varchar(4096)"
                        + ");");
        connection.createStatement().execute("CREATE TABLE stored_requests (id SERIAL PRIMARY KEY, "
                + "accountId varchar(40) NOT NULL, reqid varchar(40) NOT NULL, requestData varchar(512));");
        connection.createStatement().execute("CREATE TABLE stored_requests2 (id SERIAL PRIMARY KEY, "
                + "accountId varchar(40) NOT NULL, reqid varchar(40) NOT NULL, requestData varchar(512));");
        connection.createStatement().execute("CREATE TABLE stored_imps (id SERIAL PRIMARY KEY, "
                + "accountId varchar(40) NOT NULL, impid varchar(40) NOT NULL, impData varchar(512));");
        connection.createStatement().execute("CREATE TABLE stored_imps2 (id SERIAL PRIMARY KEY, "
                + "accountId varchar(40) NOT NULL, impid varchar(40) NOT NULL, impData varchar(512));");
        connection.createStatement().execute(
                "CREATE TABLE stored_responses (id SERIAL PRIMARY KEY, responseId varchar(40) NOT NULL,"
                        + " responseData varchar(512));");
        connection.createStatement().execute("CREATE TABLE one_column_table (id SERIAL PRIMARY KEY, reqid "
                + "varchar(40) NOT NULL);");
        connection.createStatement().execute("insert into accounts_account (uuid, config) values ("
                + "'1001',"
                + "'{"
                + "\"id\": \"1001\","
                + "\"status\": \"active\","
                + "\"auction\": {"
                + "\"price-granularity\": \"med\","
                + "\"debug-allow\": true,"
                + "\"banner-cache-ttl\": 100,"
                + "\"video-cache-ttl\": 100,"
                + "\"truncate-target-attr\": 0,"
                + "\"default-integration\": \"web\","
                + "\"bid-validations\": {"
                + "\"banner-creative-max-size\": \"enforce\""
                + "},"
                + "\"events\": {"
                + "\"enabled\": true"
                + "}"
                + "},"
                + "\"privacy\": {"
                + "\"gdpr\": {"
                + "\"enabled\": true,"
                + "\"integration-enabled\": {\"amp\": true, \"app\": true, \"video\": true, \"web\": true}"
                + "}"
                + "},"
                + "\"analytics\": {"
                + "\"auction-events\": {\"amp\": true},"
                + "\"modules\": {\"some-analytics\": {\"supported-endpoints\": [\"auction\"]}}"
                + "},"
                + "\"cookie-sync\": {"
                + "\"default-limit\": 5,"
                + "\"max-limit\": 8,"
                + "\"default-coop-sync\": true"
                + "}"
                + "}'"
                + ");");
        connection.createStatement().execute(
                "insert into stored_requests (accountId, reqid, requestData) values ('1001', '1','value1');");
        connection.createStatement().execute(
                "insert into stored_requests (accountId, reqid, requestData) values ('1001', '2','value2');");
        connection.createStatement().execute(
                "insert into stored_requests2 (accountId, reqid, requestData) values ('1001', '3','value3');");
        connection.createStatement().execute(
                "insert into stored_imps (accountId, impid, impData) values ('1001', '4','value4');");
        connection.createStatement().execute(
                "insert into stored_imps (accountId, impid, impData) values ('1001', '5','value5');");
        connection.createStatement().execute(
                "insert into stored_imps2 (accountId, impid, impData) values ('1001', '6','value6');");
        connection.createStatement().execute(
                "insert into stored_responses (responseId, responseData) "
                        + "values ('1', 'response1');");
        connection.createStatement().execute(
                "insert into stored_responses (responseId, responseData) "
                        + "values ('2', 'response2');");
        connection.createStatement().execute(
                "insert into one_column_table (reqid) values ('3');");
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
        jdbcApplicationSettings = new JdbcApplicationSettings(
                jdbcClient(),
                jacksonMapper,
                SELECT_ACCOUNT_QUERY,
                SELECT_QUERY,
                SELECT_QUERY,
                SELECT_RESPONSE_QUERY);
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void getAccountByIdShouldReturnAccountWithAllFieldsPopulated(TestContext context) {
        // when
        final Future<Account> future = jdbcApplicationSettings.getAccountById("1001", timeout);

        // then
        final Async async = context.async();
        future.setHandler(context.asyncAssertSuccess(account -> {
            assertThat(account).isEqualTo(Account.builder()
                    .id("1001")
                    .status(AccountStatus.active)
                    .auction(AccountAuctionConfig.builder()
                            .priceGranularity("med")
                            .bannerCacheTtl(100)
                            .videoCacheTtl(100)
                            .truncateTargetAttr(0)
                            .defaultIntegration("web")
                            .debugAllow(true)
                            .bidValidations(AccountBidValidationConfig.of(BidValidationEnforcement.enforce))
                            .events(AccountEventsConfig.of(true))
                            .build())
                    .privacy(AccountPrivacyConfig.of(
                            AccountGdprConfig.builder()
                                    .enabled(true)
                                    .enabledForRequestType(EnabledForRequestType.of(true, true, true, true))
                                    .build(),
                            null))
                    .analytics(AccountAnalyticsConfig.of(
                            singletonMap("amp", true),
                            singletonMap(
                                    "some-analytics",
                                    mapper.createObjectNode()
                                            .set("supported-endpoints", mapper.createArrayNode().add("auction")))))
                    .cookieSync(AccountCookieSyncConfig.of(5, 8, true))
                    .build());
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
            assertThat(exception).isInstanceOf(PreBidException.class)
                    .hasMessage("Account not found: non-existing");
            async.complete();
        }));
    }

    @Test
    public void getStoredDataShouldReturnExpectedResult(TestContext context) {
        // when
        final Future<StoredDataResult> future = jdbcApplicationSettings.getStoredData(
                "1001", new HashSet<>(asList("1", "2")), new HashSet<>(asList("4", "5")), timeout);

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
                "1001", new HashSet<>(asList("1", "2")), new HashSet<>(asList("3", "4")), timeout);

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
    public void getVideoStoredDataShouldReturnExpectedResult(TestContext context) {
        // when
        final Future<StoredDataResult> future = jdbcApplicationSettings.getVideoStoredData("1001",
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
    public void getVideoStoredDataShouldReturnStoredRequests(TestContext context) {
        // given
        jdbcApplicationSettings = new JdbcApplicationSettings(
                jdbcClient(),
                jacksonMapper,
                SELECT_ACCOUNT_QUERY,
                SELECT_UNION_QUERY,
                SELECT_UNION_QUERY,
                SELECT_RESPONSE_QUERY);

        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getVideoStoredData("1001", new HashSet<>(asList("1", "2", "3")),
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
    public void getStoredDataUnionSelectByIdShouldReturnStoredRequests(TestContext context) {
        // given
        jdbcApplicationSettings = new JdbcApplicationSettings(
                jdbcClient(),
                jacksonMapper,
                SELECT_ACCOUNT_QUERY,
                SELECT_UNION_QUERY,
                SELECT_UNION_QUERY,
                SELECT_RESPONSE_QUERY);

        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredData("1001", new HashSet<>(asList("1", "2", "3")),
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
        jdbcApplicationSettings = new JdbcApplicationSettings(
                jdbcClient(),
                jacksonMapper,
                SELECT_ACCOUNT_QUERY,
                SELECT_UNION_QUERY,
                SELECT_UNION_QUERY,
                SELECT_RESPONSE_QUERY);

        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getAmpStoredData("1001", new HashSet<>(asList("1", "2", "3")),
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
                jdbcApplicationSettings.getStoredData("1001", new HashSet<>(asList("1", "3")), emptySet(), timeout);

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
                jdbcApplicationSettings.getStoredData("1001", emptySet(), new HashSet<>(asList("4", "6")), timeout);

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
                jdbcApplicationSettings.getAmpStoredData("1001", new HashSet<>(asList("1", "3")), emptySet(),
                        timeout);

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
        jdbcApplicationSettings = new JdbcApplicationSettings(
                jdbcClient(),
                jacksonMapper,
                SELECT_ACCOUNT_QUERY,
                SELECT_FROM_ONE_COLUMN_TABLE_QUERY,
                SELECT_FROM_ONE_COLUMN_TABLE_QUERY,
                SELECT_RESPONSE_QUERY);

        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredData("1001", new HashSet<>(asList("1", "2", "3")), emptySet(),
                        timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredDataResult.of(emptyMap(), emptyMap(),
                    singletonList("Error occurred while mapping stored request data")));
            async.complete();
        }));
    }

    @Test
    public void getAmpStoredDataShouldReturnErrorIfResultContainsLessColumnsThanExpected(TestContext context) {
        // given
        jdbcApplicationSettings = new JdbcApplicationSettings(
                jdbcClient(),
                jacksonMapper,
                SELECT_ACCOUNT_QUERY,
                SELECT_FROM_ONE_COLUMN_TABLE_QUERY,
                SELECT_FROM_ONE_COLUMN_TABLE_QUERY,
                SELECT_RESPONSE_QUERY);

        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getAmpStoredData("1001", new HashSet<>(asList("1", "2", "3")), emptySet(),
                        timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredDataResult.of(emptyMap(), emptyMap(),
                    singletonList("Error occurred while mapping stored request data")));
            async.complete();
        }));
    }

    @Test
    public void getStoredDataShouldReturnErrorAndEmptyResult(TestContext context) {
        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getStoredData("1001", new HashSet<>(asList("3", "4")),
                        new HashSet<>(asList("6", "7")), timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredDataResult.of(emptyMap(), emptyMap(),
                    singletonList("No stored requests for ids [3, 4] and stored imps for ids [6, 7] were found")));
            async.complete();
        }));
    }

    @Test
    public void getAmpStoredDataShouldReturnErrorAndEmptyResult(TestContext context) {
        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getAmpStoredData("1001", new HashSet<>(asList("3", "4")), emptySet(),
                        timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredDataResult.of(emptyMap(), emptyMap(),
                    singletonList("No stored requests for ids [3, 4] were found")));
            async.complete();
        }));
    }

    @Test
    public void getAmpStoredDataShouldIgnoreImpIdsArgument(TestContext context) {
        // when
        final Future<StoredDataResult> storedRequestResultFuture =
                jdbcApplicationSettings.getAmpStoredData("1001", singleton("1"), singleton("4"), timeout);

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredDataResult.of(singletonMap("1", "value1"), emptyMap(),
                    emptyList()));
            async.complete();
        }));
    }

    @Test
    public void getStoredResponseShouldReturnExpectedResult(TestContext context) {
        // when
        final Future<StoredResponseDataResult> future = jdbcApplicationSettings.getStoredResponses(
                new HashSet<>(asList("1", "2")), timeout);

        // then
        final Async async = context.async();
        final Map<String, String> expectedResponses = new HashMap<>();
        expectedResponses.put("1", "response1");
        expectedResponses.put("2", "response2");

        future.setHandler(context.asyncAssertSuccess(storedResponseDataResult -> {
            assertThat(storedResponseDataResult)
                    .isEqualTo(StoredResponseDataResult.of(expectedResponses, emptyList()));
            async.complete();
        }));
    }

    @Test
    public void getStoredResponseShouldReturnResultWithErrorIfNotAllStoredResponsesWereFound(TestContext context) {
        // when
        final Future<StoredResponseDataResult> storedResponseDataResultFuture =
                jdbcApplicationSettings.getStoredResponses(new HashSet<>(asList("1", "3")), timeout);

        // then
        final Async async = context.async();
        storedResponseDataResultFuture.setHandler(context.asyncAssertSuccess(storedResponseDataResult -> {
            assertThat(storedResponseDataResult).isEqualTo(StoredResponseDataResult.of(singletonMap("1", "response1"),
                    singletonList("No stored response found for id: 3")));
            async.complete();
        }));
    }

    @Test
    public void getStoredResponseShouldReturnErrorIfResultContainsLessColumnsThanExpected(TestContext context) {
        // given
        jdbcApplicationSettings = new JdbcApplicationSettings(
                jdbcClient(),
                jacksonMapper,
                SELECT_ACCOUNT_QUERY,
                SELECT_QUERY,
                SELECT_QUERY,
                SELECT_ONE_COLUMN_RESPONSE_QUERY);

        // when
        final Future<StoredResponseDataResult> storedResponseDataResultFuture =
                jdbcApplicationSettings.getStoredResponses(new HashSet<>(asList("1", "2", "3")), timeout);

        // then
        final Async async = context.async();
        storedResponseDataResultFuture.setHandler(context.asyncAssertSuccess(storedResponseDataResult -> {
            assertThat(storedResponseDataResult).isEqualTo(StoredResponseDataResult.of(emptyMap(),
                    singletonList("Result set column number is less than expected")));
            async.complete();
        }));
    }

    @Test
    public void getStoredResponseShouldReturnErrorAndEmptyResult(TestContext context) {
        // when
        final Future<StoredResponseDataResult> storedResponseDataResultFuture =
                jdbcApplicationSettings.getStoredResponses(new HashSet<>(asList("3", "4")), timeout);

        // then
        final Async async = context.async();
        storedResponseDataResultFuture.setHandler(context.asyncAssertSuccess(storedResponseDataResult -> {
            assertThat(storedResponseDataResult).isEqualTo(StoredResponseDataResult.of(emptyMap(),
                    singletonList("No stored responses were found for ids: 3,4")));
            async.complete();
        }));
    }

    private JdbcClient jdbcClient() {
        return new BasicJdbcClient(vertx, JDBCClient.createShared(vertx,
                new JsonObject()
                        .put("url", JDBC_URL)
                        .put("driver_class", "org.h2.Driver")
                        .put("max_pool_size", 10)), metrics, clock);
    }
}
