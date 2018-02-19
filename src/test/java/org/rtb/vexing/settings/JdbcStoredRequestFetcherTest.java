package org.rtb.vexing.settings;

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
import org.rtb.vexing.execution.GlobalTimeout;
import org.rtb.vexing.settings.model.StoredRequestResult;
import org.rtb.vexing.vertx.JdbcClient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@RunWith(VertxUnitRunner.class)
public class JdbcStoredRequestFetcherTest {

    private static final String JDBC_URL = "jdbc:h2:mem:test";
    private static final String selectQuery = "SELECT reqid, requestData FROM stored_requests WHERE reqid IN " +
            "(%ID_LIST%)";
    private static final String selectUnionQuery = "SELECT reqid, requestData FROM stored_requests where reqid IN " +
            "(%ID_LIST%) UNION SELECT reqid, requestData FROM stored_requests2 where reqid IN (%ID_LIST%)";
    private static final String selectFromOneColumnTableQuery = "SELECT reqid FROM one_column_table WHERE reqid IN " +
            "(%ID_LIST%)";

    private static Connection connection;

    private Vertx vertx;

    private JdbcStoredRequestFetcher jdbcStoredRequestFetcher;

    @BeforeClass
    public static void beforeClass() throws SQLException {
        connection = DriverManager.getConnection(JDBC_URL);
        connection.createStatement().execute("CREATE TABLE stored_requests (id SERIAL PRIMARY KEY, reqid varchar(40) "
                + "NOT NULL, requestData varchar(512));");
        connection.createStatement().execute("CREATE TABLE stored_requests2 (id SERIAL PRIMARY KEY, reqid varchar(40) "
                + "NOT NULL, requestData varchar(512));");
        connection.createStatement().execute("CREATE TABLE one_column_table (id SERIAL PRIMARY KEY, reqid varchar(40)"
                + " NOT NULL);");
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

        this.jdbcStoredRequestFetcher = new JdbcStoredRequestFetcher(new JdbcClient(vertx, jdbcClient(vertx)),
                selectQuery);
    }

    private static JDBCClient jdbcClient(Vertx vertx) {
        return JDBCClient.createShared(vertx, new JsonObject()
                .put("url", JDBC_URL)
                .put("driver_class", "org.h2.Driver")
                .put("max_pool_size", 10));
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new JdbcStoredRequestFetcher(null, null));
        assertThatNullPointerException().isThrownBy(
                () -> new JdbcStoredRequestFetcher(new JdbcClient(vertx, jdbcClient(vertx)), null));
    }

    @Test
    public void getStoredRequestByIdShouldReturnFailedFutureWithNullPointerExceptionCause() {
        assertThatNullPointerException().isThrownBy(() -> jdbcStoredRequestFetcher.getStoredRequestsById(null, null));
        assertThatNullPointerException().isThrownBy(
                () -> jdbcStoredRequestFetcher.getStoredRequestsById(emptySet(), null));
    }

    @Test
    public void getStoredRequestsByIdsShouldReturnStoredRequests(TestContext context) {
        // when
        final Future<StoredRequestResult> future =
                jdbcStoredRequestFetcher.getStoredRequestsById(new HashSet<>(asList("1", "2")), timeout());

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
    public void getStoredRequestsUnionSelectByIdsShouldReturnStoredRequests(TestContext context) {
        // given
        jdbcStoredRequestFetcher = new JdbcStoredRequestFetcher(new JdbcClient(vertx, jdbcClient(vertx)),
                selectUnionQuery);

        // when
        final Future<StoredRequestResult> storedRequestResultFuture =
                jdbcStoredRequestFetcher.getStoredRequestsById(new HashSet<>(asList("1", "2", "3")), timeout());

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
    public void getStoredRequestsByIdsShouldReturnStoredRequestsWithError(TestContext context) {
        // when
        final Future<StoredRequestResult> storedRequestResultFuture =
                jdbcStoredRequestFetcher.getStoredRequestsById(new HashSet<>(asList("1", "3")), timeout());

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
        jdbcStoredRequestFetcher = new JdbcStoredRequestFetcher(new JdbcClient(vertx, jdbcClient(vertx)),
                selectFromOneColumnTableQuery);

        // when
        final Future<StoredRequestResult> storedRequestResultFuture =
                jdbcStoredRequestFetcher.getStoredRequestsById(new HashSet<>(asList("1", "2", "3")), timeout());

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredRequestResult.of(emptyMap(),
                    singletonList("Result set column number is less than expected")));
            async.complete();
        }));
    }

    @Test
    public void getStoredRequestsByIdsShouldReturnErrorAndEmptyResult(TestContext context) {
        // when
        final Future<StoredRequestResult> storedRequestResultFuture =
                jdbcStoredRequestFetcher.getStoredRequestsById(new HashSet<>(asList("3", "4")), timeout());

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredRequestResult.of(emptyMap(),
                    singletonList("Stored requests for ids [3, 4] was not found")));
            async.complete();
        }));
    }

    private static GlobalTimeout timeout() {
        return GlobalTimeout.create(500);
    }
}
