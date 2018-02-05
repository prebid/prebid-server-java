package org.rtb.vexing.settings;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.rtb.vexing.settings.model.StoredRequestResult;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

@RunWith(VertxUnitRunner.class)
public class JdbcStoredRequestFetcherTest {

    private static final String JDBC_URL = "jdbc:h2:mem:test";
    private Vertx vertx;
    private JdbcStoredRequestFetcher jdbcStoredRequestFetcher;
    private static Connection connection;
    private final String selectQuery = "SELECT reqid, requestData FROM stored_requests WHERE reqid IN (%ID_LIST%)";
    private final String selectUnionQuery = "SELECT reqid, requestData FROM stored_requests where reqid IN (%ID_LIST%) "
            + "UNION SELECT reqid, requestData FROM stored_requests2 where reqid IN (%ID_LIST%)";
    private final String selectFromOneColumnTableQuery = "SELECT reqid FROM one_column_table WHERE reqid IN (%ID_LIST%)";

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
    public void setUp(TestContext context) {
        vertx = Vertx.vertx();

        this.jdbcStoredRequestFetcher = JdbcStoredRequestFetcher.create(vertx, JDBC_URL, "org.h2.Driver", 10,
                selectQuery);
    }

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> JdbcStoredRequestFetcher.create(null, null, null, 0, null));
        assertThatNullPointerException().isThrownBy(() -> JdbcStoredRequestFetcher.create(vertx, null, null, 0, null));
        assertThatNullPointerException().isThrownBy(() -> JdbcStoredRequestFetcher.create(vertx, "url", "org.h2.Driver",
                0, null));
        assertThatIllegalArgumentException().isThrownBy(
                () -> JdbcStoredRequestFetcher.create(vertx, "url", "driverClass", 0, selectQuery))
                .withMessage("maxPoolSize must be positive");
    }

    @Test
    public void getStoredRequestByIdShouldReturnFailedFutureWithNullPointerExceptionCause() {
        assertThatNullPointerException().isThrownBy(() -> jdbcStoredRequestFetcher.getStoredRequestsById(null));
    }

    @Test
    public void getStoredRequestsByIdsShouldReturnStoredRequests(TestContext context) {
        // when
        final Future<StoredRequestResult> future =
                jdbcStoredRequestFetcher.getStoredRequestsById(new HashSet<>(asList("1", "2")));

        // then
        final Async async = context.async();
        final Map<String, String> expectedResultMap = new HashMap<>();
        expectedResultMap.put("1", "value1");
        expectedResultMap.put("2", "value2");
        future.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredRequestResult
                    .of(expectedResultMap, Collections.emptyList()));
            async.complete();
        }));
    }

    @Test
    public void getStoredRequestsUnionSelectByIdsShouldReturnStoredRequests(TestContext context) {
        // given
        JdbcStoredRequestFetcher.create(vertx, JDBC_URL, "org.h2.Driver", 10, selectUnionQuery)
                // when
                .getStoredRequestsById(new HashSet<>(asList("1", "2", "3")))
                .setHandler(context.asyncAssertSuccess(res -> {
                    // then
                    final Map<String, String> expectedResultMap = new HashMap<>();
                    expectedResultMap.put("1", "value1");
                    expectedResultMap.put("2", "value2");
                    expectedResultMap.put("3", "value3");

                    assertThat(res).isEqualTo(StoredRequestResult
                                .of(expectedResultMap, Collections.emptyList()));

                }));
    }

    @Test
    public void getStoredRequestsByIdsShouldReturnStoredRequestsWithError(TestContext context) {
        // when
        final Future<StoredRequestResult> storedRequestResultFuture = jdbcStoredRequestFetcher.getStoredRequestsById(
                new HashSet<>(asList("1", "3")));

        // then
        final Async async = context.async();
        final Map<String, String> expectedResultMap = new HashMap<>();
        expectedResultMap.put("1", "value1");
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredRequestResult.of(expectedResultMap,
                    Collections.singletonList("No config found for id: 3")));
            async.complete();
        }));
    }

    @Test
    public void getStoredRequestByIdShouldReturnErrorIfResultContainsLessColumnsThanExpected(TestContext context) {
        // given
        JdbcStoredRequestFetcher.create(vertx, JDBC_URL, "org.h2.Driver", 10, selectFromOneColumnTableQuery)
                 // when
                .getStoredRequestsById(new HashSet<>(asList("1", "2", "3")))
                .setHandler(context.asyncAssertSuccess(storedRequestResult -> {
                    // then
                    final Map<String, String> expectedResultMap = Collections.emptyMap();
                    assertThat(storedRequestResult).isEqualTo(StoredRequestResult.of(expectedResultMap,
                            Collections.singletonList("Result set column number is less than expected")));
                }));
    }

    @Test
    public void getStoredRequestsByIdsShouldReturnErrorAndEmptyResult(TestContext context) {
        // when
        final Future<StoredRequestResult> storedRequestResultFuture = jdbcStoredRequestFetcher.getStoredRequestsById(
                new HashSet<>(asList("3", "4")));

        // then
        final Async async = context.async();
        storedRequestResultFuture.setHandler(context.asyncAssertSuccess(storedRequestResult -> {
            assertThat(storedRequestResult).isEqualTo(StoredRequestResult.of(Collections.emptyMap(),
                    Collections.singletonList("Stored requests for ids [3, 4] was not found")));
            async.complete();
        }));
    }
}
