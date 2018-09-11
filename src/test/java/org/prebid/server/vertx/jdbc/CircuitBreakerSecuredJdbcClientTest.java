package org.prebid.server.vertx.jdbc;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
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
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@RunWith(VertxUnitRunner.class)
public class CircuitBreakerSecuredJdbcClientTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private Vertx vertx;
    @Mock
    private BasicJdbcClient basicJdbcClient;
    @Mock
    private Metrics metrics;

    private CircuitBreakerSecuredJdbcClient jdbcClient;

    private Timeout timeout;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
        final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
        timeout = new TimeoutFactory(clock).create(500L);

        jdbcClient = new CircuitBreakerSecuredJdbcClient(vertx, basicJdbcClient, metrics, 0, 100L, 200L);
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> new CircuitBreakerSecuredJdbcClient(null, null, null, 0, 0L, 0L));
        assertThatNullPointerException().isThrownBy(
                () -> new CircuitBreakerSecuredJdbcClient(vertx, null, null, 0, 0L, 0L));
        assertThatNullPointerException().isThrownBy(
                () -> new CircuitBreakerSecuredJdbcClient(vertx, basicJdbcClient, null, 0, 0L, 0L));
    }

    @Test
    public void executeQueryShouldReturnResultIfCircuitIsClosedAndQuerySucceeded(TestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(
                Future.succeededFuture("value")));

        // when
        final Future<?> future = jdbcClient.executeQuery("query", emptyList(),
                resultSet -> resultSet.getResults().get(0).getString(0), timeout);

        // then
        future.setHandler(context.asyncAssertSuccess(result ->
                assertThat(result).isEqualTo("value")));
    }

    @Test
    public void executeQueryShouldReturnExceptionIfCircuitIsClosedAndQueryFails(TestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(
                Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Future<?> future = jdbcClient.executeQuery("query", emptyList(), identity(), timeout);

        // then
        future.setHandler(context.asyncAssertFailure(throwable ->
                assertThat(throwable).isInstanceOf(RuntimeException.class).hasMessage("exception1")));
    }

    @Test
    public void executeQueryShouldNotExecuteQueryIfCircuitIsOpened(TestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(
                Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Future<?> future = jdbcClient.executeQuery("query", emptyList(), identity(), timeout) // 1 call
                .recover(ignored -> jdbcClient.executeQuery("query", emptyList(), identity(), timeout)); // 2 call

        // then
        future.setHandler(context.asyncAssertFailure(throwable -> {
            assertThat(throwable).isInstanceOf(RuntimeException.class).hasMessage("open circuit");

            verify(basicJdbcClient, times(1))
                    .executeQuery(any(), any(), any(), any()); // invoked only on 1 call
        }));
    }

    @Test
    public void executeQueryShouldReturnExceptionIfCircuitIsHalfOpenedAndQueryFails(TestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(
                Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Async async = context.async();
        jdbcClient.executeQuery("query", emptyList(), identity(), timeout) // 1 call
                .recover(ignored -> jdbcClient.executeQuery("query", emptyList(), identity(), timeout)) // 2 call
                .setHandler(ignored -> vertx.setTimer(300L, id -> async.complete()));
        async.await();

        final Future<?> future = jdbcClient.executeQuery("query", emptyList(), identity(), timeout); // 3 call

        // then
        future.setHandler(context.asyncAssertFailure(exception -> {
            assertThat(exception).isInstanceOf(RuntimeException.class).hasMessage("exception1");

            verify(basicJdbcClient, times(2))
                    .executeQuery(any(), any(), any(), any()); // invoked only on 1 & 3 calls
        }));
    }

    @Test
    public void executeQueryShouldReturnResultIfCircuitIsHalfOpenedAndQuerySucceeded(TestContext context) {
        // given
        givenExecuteQueryReturning(Arrays.asList(
                Future.failedFuture(new RuntimeException("exception1")),
                Future.succeededFuture("value")));

        // when
        final Async async = context.async();
        jdbcClient.executeQuery("query", emptyList(), identity(), timeout) // 1 call
                .recover(ignored -> jdbcClient.executeQuery("query", emptyList(), identity(), timeout)) // 2 call
                .setHandler(ignored -> vertx.setTimer(300L, id -> async.complete()));
        async.await();

        final Future<?> future = jdbcClient.executeQuery("query", emptyList(),
                resultSet -> resultSet.getResults().get(0).getString(0), timeout); // 3 call

        // then
        future.setHandler(context.asyncAssertSuccess(result -> {
            assertThat(result).isEqualTo("value");

            verify(basicJdbcClient, times(2))
                    .executeQuery(any(), any(), any(), any()); // invoked only on 1 & 3 calls
        }));
    }

    @Test
    public void executeQueryShouldReportMetricsOnCircuitOpened(TestContext context) {
        // given
        givenExecuteQueryReturning(singletonList(
                Future.failedFuture(new RuntimeException("exception1"))));

        // when
        final Future<?> future = jdbcClient.executeQuery("query", emptyList(), identity(), timeout);

        // then
        future.setHandler(context.asyncAssertFailure(throwable ->
                verify(metrics).updateDatabaseCircuitBreakerMetric(eq(true))));
    }

    @Test
    public void executeQueryShouldReportMetricsOnCircuitClosed(TestContext context) {
        // given
        givenExecuteQueryReturning(Arrays.asList(
                Future.failedFuture(new RuntimeException("exception1")),
                Future.succeededFuture()));

        // when
        final Async async = context.async();
        jdbcClient.executeQuery("query", emptyList(), identity(), timeout) // 1 call
                .recover(ignored -> jdbcClient.executeQuery("query", emptyList(), identity(), timeout)) // 2 call
                .setHandler(ignored -> vertx.setTimer(300L, id -> async.complete()));
        async.await();

        final Future<?> future = jdbcClient.executeQuery("query", emptyList(),
                resultSet -> resultSet.getResults().get(0).getString(0), timeout); // 3 call

        // then
        future.setHandler(context.asyncAssertSuccess(result ->
                verify(metrics).updateDatabaseCircuitBreakerMetric(eq(false))));
    }

    @SuppressWarnings("unchecked")
    private <T> void givenExecuteQueryReturning(List<Future<T>> results) {
        BDDMockito.BDDMyOngoingStubbing<Future<Object>> given =
                given(basicJdbcClient.executeQuery(any(), any(), any(), any()));
        for (Future<T> result : results) {
            given = given.willReturn((Future<Object>) result);
        }
    }
}
