package org.prebid.server.vertx.database;

import io.vertx.core.Future;
import io.vertx.sqlclient.Pool;
import io.vertx.sqlclient.PreparedQuery;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import lombok.Value;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.withSettings;
import static org.mockito.quality.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
public class BasicDatabaseClientTest {

    @Mock
    private Pool pool;
    @Mock
    private Metrics metrics;

    private Clock clock;
    private BasicDatabaseClient target;

    private Timeout timeout;

    @BeforeEach
    public void setUp() {
        clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500L);

        target = new BasicDatabaseClient(pool, metrics, clock);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new BasicDatabaseClient(null, null, null));
        assertThatNullPointerException().isThrownBy(() -> new BasicDatabaseClient(pool, null, null));
        assertThatNullPointerException().isThrownBy(() -> new BasicDatabaseClient(pool, metrics, null));
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
        verifyNoMoreInteractions(pool);
    }

    @Test
    public void executeQueryShouldReturnFailedFutureIfItTakesLongerThanRemainingTimeout() {
        // given
        final SqlConnection connection = mock(SqlConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));
        givenQueryReturning(connection, Future.failedFuture(new TimeoutException("Some text")));

        // when
        final Future<RowSet<Row>> future = target.executeQuery("query", emptyList(), identity(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(TimeoutException.class)
                .hasMessage("Timed out while executing SQL query");
    }

    @Test
    public void executeQueryShouldReturnFailedFutureIfConnectionAcquisitionFails() {
        // given
        givenGetConnectionReturning(Future.failedFuture(new RuntimeException("Failed to acquire connection")));

        // when
        final Future<RowSet<Row>> future = target.executeQuery("query", emptyList(), identity(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Failed to acquire connection");
    }

    @Test
    public void executeQueryShouldReturnFailedFutureIfQueryFails() {
        // given
        final SqlConnection connection = mock(SqlConnection.class);
        givenGetConnectionReturning(Future.succeededFuture(connection));

        givenQueryReturning(connection, Future.failedFuture(new RuntimeException("Failed to execute query")));

        // when
        final Future<RowSet<Row>> future = target.executeQuery("query", emptyList(), identity(), timeout);

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Failed to execute query");
    }

    @Test
    public void executeQueryShouldReturnSucceededFutureWithMappedQueryResult() {
        // given
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
    private static void givenQueryReturning(SqlConnection connection, Future<RowSet<Row>> result) {
        final PreparedQuery<RowSet<Row>> preparedQueryMock = mock(PreparedQuery.class);
        given(connection.preparedQuery(anyString())).willReturn(preparedQueryMock);
        given(preparedQueryMock.execute(any(Tuple.class))).willReturn(result);
    }

    private void givenGetConnectionReturning(Future<SqlConnection> result) {
        given(pool.getConnection()).willReturn(result);
    }

    private Timeout expiredTimeout() {
        return new TimeoutFactory(clock).create(clock.instant().minusMillis(1500L).toEpochMilli(), 1000L);
    }

    private RowSet<Row> givenRowSet(Row... rows) {
        final RowSet<Row> rowSet = mock(RowSet.class, withSettings().strictness(LENIENT));
        given(rowSet.iterator()).willReturn(CustomRowIterator.of(Arrays.asList(rows).iterator()));
        return rowSet;
    }

    private Row givenRow(Object... values) {
        final Row row = mock(Row.class, withSettings().strictness(LENIENT));
        given(row.getString(anyInt())).willAnswer(invocation -> values[(Integer) invocation.getArgument(0)]);
        given(row.getValue(anyInt())).willAnswer(invocation -> values[(Integer) invocation.getArgument(0)]);
        return row;
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
}
