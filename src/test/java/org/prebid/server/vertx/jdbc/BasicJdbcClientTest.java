package org.prebid.server.vertx.jdbc;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.metric.Metrics;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.TimeoutException;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class BasicJdbcClientTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;
    @Mock
    private JDBCClient vertxJdbcClient;
    @Mock
    private Metrics metrics;

    private Clock clock;
    private BasicJdbcClient jdbcClient;

    private Timeout timeout;

    @Before
    public void setUp() {
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500L);

        jdbcClient = new BasicJdbcClient(vertx, vertxJdbcClient, metrics, clock);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new BasicJdbcClient(null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new BasicJdbcClient(vertx, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new BasicJdbcClient(vertx, vertxJdbcClient, null, null));
        assertThatNullPointerException().isThrownBy(() -> new BasicJdbcClient(vertx, vertxJdbcClient, metrics, null));
    }

    @Test
    public void initializeShouldReturnEmptySucceededFutureIfConnectionCouldBeEstablished() {
        // given
        givenGetConnectionReturning(Future.succeededFuture());

        // when
        final Future<Void> future = jdbcClient.initialize();

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNull();
    }

    @Test
    public void initializeShouldReturnFailedFutureIfConnectionCouldNotBeEstablished() {
        // given
        givenGetConnectionReturning(Future.failedFuture(new RuntimeException("Failed to open connection")));

        // when
        final Future<Void> future = jdbcClient.initialize();

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Failed to open connection");
    }

    @Test
    public void executeQueryShouldReturnFailedFutureIfGlobalTimeoutAlreadyExpired() {
        // when
        final Future<ResultSet> future = jdbcClient.executeQuery("query", emptyList(), identity(), expiredTimeout());

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(TimeoutException.class)
                .hasMessage("Timed out while executing SQL query");
        verifyNoMoreInteractions(vertx, vertxJdbcClient);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void executeQueryShouldReturnFailedFutureIfItTakesLongerThanRemainingTimeout() {
        // given
        given(vertx.setTimer(anyLong(), any())).willAnswer(invocation -> {
            ((Handler<Long>) invocation.getArgument(1)).handle(123L);
            return 123L;
        });

        final SQLConnection connection = mock(SQLConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));

        givenQueryReturning(connection, Future.succeededFuture(new ResultSet()));

        // when
        final Future<ResultSet> future = jdbcClient.executeQuery("query", emptyList(), identity(), timeout);

        // then
        final ArgumentCaptor<Long> timeoutCaptor = ArgumentCaptor.forClass(Long.class);
        verify(vertx).setTimer(timeoutCaptor.capture(), any());
        assertThat(timeoutCaptor.getValue()).isEqualTo(500L);

        verify(vertx).cancelTimer(eq(123L));

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(TimeoutException.class)
                .hasMessage("Timed out while executing SQL query");
    }

    @Test
    public void executeQueryShouldReturnFailedFutureIfConnectionAcquisitionFails() {
        // given
        given(vertx.setTimer(anyLong(), any())).willReturn(123L);

        givenGetConnectionReturning(Future.failedFuture(new RuntimeException("Failed to acquire connection")));

        // when
        final Future<ResultSet> future = jdbcClient.executeQuery("query", emptyList(), identity(), timeout);

        // then
        verify(vertx).cancelTimer(eq(123L));

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Failed to acquire connection");
    }

    @Test
    public void executeQueryShouldReturnFailedFutureIfQueryFails() {
        // given
        given(vertx.setTimer(anyLong(), any())).willReturn(123L);

        final SQLConnection connection = mock(SQLConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));

        givenQueryReturning(connection, Future.failedFuture(new RuntimeException("Failed to execute query")));

        // when
        final Future<ResultSet> future = jdbcClient.executeQuery("query", emptyList(), identity(), timeout);

        // then
        verify(vertx).cancelTimer(eq(123L));

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Failed to execute query");
    }

    @Test
    public void executeQueryShouldReturnSucceededFutureWithMappedQueryResult() {
        // given
        given(vertx.setTimer(anyLong(), any())).willReturn(123L);

        final SQLConnection connection = mock(SQLConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));

        givenQueryReturning(connection, Future.succeededFuture(
                new ResultSet().setResults(singletonList(new JsonArray().add("value")))));

        // when
        final Future<String> future = jdbcClient.executeQuery("query", emptyList(),
                resultSet -> resultSet.getResults().get(0).getString(0), timeout);

        // then
        verify(vertx).cancelTimer(eq(123L));

        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo("value");
    }

    @Test
    public void executeQueryShouldReportMetricsIfQueryFails() {
        // given
        final SQLConnection connection = mock(SQLConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));

        givenQueryReturning(connection, Future.failedFuture(new RuntimeException("Failed to execute query")));

        // when
        final Future<ResultSet> future = jdbcClient.executeQuery("query", emptyList(), identity(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        verify(metrics).updateDatabaseQueryTimeMetric(anyLong());
    }

    @Test
    public void executeQueryShouldReportMetricsIfQuerySucceeds() {
        // given
        final SQLConnection connection = mock(SQLConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));

        givenQueryReturning(connection, Future.succeededFuture(new ResultSet().setResults(emptyList())));

        // when
        final Future<String> future = jdbcClient.executeQuery("query", emptyList(), Object::toString, timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        verify(metrics).updateDatabaseQueryTimeMetric(anyLong());
    }

    @SuppressWarnings("unchecked")
    private void givenGetConnectionReturning(AsyncResult<SQLConnection> result) {
        given(vertxJdbcClient.getConnection(any())).willAnswer(invocation -> {
            ((Handler<AsyncResult<SQLConnection>>) invocation.getArgument(0)).handle(result);
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private static void givenQueryReturning(SQLConnection connection, AsyncResult<ResultSet> result) {
        given(connection.queryWithParams(anyString(), any(), any())).willAnswer(invocation -> {
            ((Handler<AsyncResult<ResultSet>>) invocation.getArgument(2)).handle(result);
            return null;
        });
    }

    private Timeout expiredTimeout() {
        return new TimeoutFactory(clock).create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);
    }
}
