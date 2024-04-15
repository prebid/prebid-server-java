package org.prebid.server.vertx.jdbc;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PreparedQuery;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import lombok.Value;
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
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.TimeoutException;

import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class BasicJdbcClientTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;
    @Mock
    private Pool pool;
    @Mock
    private Metrics metrics;

    private Clock clock;
    private BasicJdbcClient target;

    private Timeout timeout;

    @Before
    public void setUp() {
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500L);

        target = new BasicJdbcClient(vertx, pool, metrics, clock);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new BasicJdbcClient(null, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new BasicJdbcClient(vertx, null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new BasicJdbcClient(vertx, pool, null, null));
        assertThatNullPointerException().isThrownBy(() -> new BasicJdbcClient(vertx, pool, metrics, null));
    }

    @Test
    public void initializeShouldReturnEmptySucceededFutureIfConnectionCouldBeEstablished() {
        // given
        givenGetConnectionReturning(Future.succeededFuture());

        // when
        final Future<Void> future = target.initialize();

        // then
        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isNull();
    }

    @Test
    public void initializeShouldReturnFailedFutureIfConnectionCouldNotBeEstablished() {
        // given
        givenGetConnectionReturning(Future.failedFuture(new RuntimeException("Failed to open connection")));

        // when
        final Future<Void> future = target.initialize();

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Failed to open connection");
    }

    @Test
    public void executeQueryShouldReturnFailedFutureIfGlobalTimeoutAlreadyExpired() {
        // when
        final Future<RowSet<Row>> future = target.executeQuery("query", emptyList(), identity(), expiredTimeout());

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(TimeoutException.class)
                .hasMessage("Timed out while executing SQL query");
        verifyNoMoreInteractions(vertx, pool);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void executeQueryShouldReturnFailedFutureIfItTakesLongerThanRemainingTimeout() {
        // given
        given(vertx.setTimer(anyLong(), any())).willAnswer(invocation -> {
            ((Handler<Long>) invocation.getArgument(1)).handle(123L);
            return 123L;
        });

        final SqlConnection connection = mock(SqlConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));

        givenQueryReturning(connection, Future.succeededFuture(givenRowSet()));

        // when
        final Future<RowSet<Row>> future = target.executeQuery("query", emptyList(), identity(), timeout);

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
        final Future<RowSet<Row>> future = target.executeQuery("query", emptyList(), identity(), timeout);

        // then
        verify(vertx).cancelTimer(eq(123L));

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Failed to acquire connection");
    }

    @Test
    public void executeQueryShouldReturnFailedFutureIfQueryFails() {
        // given
        given(vertx.setTimer(anyLong(), any())).willReturn(123L);

        final SqlConnection connection = mock(SqlConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));

        givenQueryReturning(connection, Future.failedFuture(new RuntimeException("Failed to execute query")));

        // when
        final Future<RowSet<Row>> future = target.executeQuery("query", emptyList(), identity(), timeout);

        // then
        verify(vertx).cancelTimer(eq(123L));

        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Failed to execute query");
    }

    @Test
    public void executeQueryShouldReturnSucceededFutureWithMappedQueryResult() {
        // given
        given(vertx.setTimer(anyLong(), any())).willReturn(123L);

        final SqlConnection connection = mock(SqlConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));

        final RowSet<Row> rowSet = givenRowSet(givenRow("value"));
        givenQueryReturning(connection, Future.succeededFuture(rowSet));

        // when
        final Future<String> future = target.executeQuery(
                "query",
                emptyList(),
                rs -> rs.iterator().next().getString(0),
                timeout);

        // then
        verify(vertx).cancelTimer(eq(123L));

        assertThat(future.succeeded()).isTrue();
        assertThat(future.result()).isEqualTo("value");
    }

    @Test
    public void executeQueryShouldReportMetricsIfQueryFails() {
        // given
        final SqlConnection connection = mock(SqlConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));

        givenQueryReturning(connection, Future.failedFuture(new RuntimeException("Failed to execute query")));

        // when
        final Future<RowSet<Row>> future = target.executeQuery("query", emptyList(), identity(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        verify(metrics).updateDatabaseQueryTimeMetric(anyLong());
    }

    @Test
    public void executeQueryShouldReportMetricsIfQuerySucceeds() {
        // given
        final SqlConnection connection = mock(SqlConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));

        givenQueryReturning(connection, Future.succeededFuture(givenRowSet()));

        // when
        final Future<String> future = target.executeQuery("query", emptyList(), Object::toString, timeout);

        // then
        assertThat(future.succeeded()).isTrue();
        verify(metrics).updateDatabaseQueryTimeMetric(anyLong());
    }

    @SuppressWarnings("unchecked")
    private static void givenQueryReturning(SqlConnection connection, AsyncResult<RowSet<Row>> result) {
        final PreparedQuery<RowSet<Row>> preparedQueryMock = mock(PreparedQuery.class);
        given(connection.preparedQuery(anyString())).willReturn(preparedQueryMock);

        doAnswer(invocation -> {
            ((Handler<AsyncResult<RowSet<Row>>>) invocation.getArgument(1)).handle(result);
            return null;
        }).when(preparedQueryMock).execute(any(), any());
    }

    private Timeout expiredTimeout() {
        return new TimeoutFactory(clock).create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);
    }

    @Value(staticConstructor = "of")
    private static class CustomRowIterator implements RowIterator<Row> {

        Iterator<Row> delegate;

        @Override
        public boolean hasNext() {
            return delegate.hasNext();
        }

        @Override
        public Row next() {
            return delegate.next();
        }
    }

    @SuppressWarnings("unchecked")
    private void givenGetConnectionReturning(AsyncResult<SqlConnection> result) {
        doAnswer(invocation -> {
            ((Handler<AsyncResult<SqlConnection>>) invocation.getArgument(0)).handle(result);
            return null;
        }).when(pool).getConnection(any());
    }

    private RowSet<Row> givenRowSet(Row... rows) {
        final RowSet<Row> rowSet = mock(RowSet.class);
        given(rowSet.iterator()).willReturn(CustomRowIterator.of(Arrays.asList(rows).iterator()));
        return rowSet;
    }

    private Row givenRow(Object... values) {
        final Row row = mock(Row.class);
        given(row.getString(anyInt())).willAnswer(invocation -> values[(Integer) invocation.getArgument(0)]);
        given(row.getValue(anyInt())).willAnswer(invocation -> values[(Integer) invocation.getArgument(0)]);
        return row;
    }
}
