package org.prebid.server.hooks.modules.intentiq.identity.cache;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.hooks.modules.intentiq.identity.metric.IntentiqIdentityMetrics;

import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RedisStatsReporterTest {

    @Mock
    private RedisIdentityStore store;

    @Mock
    private Vertx vertx;

    @Mock
    private IntentiqIdentityMetrics metrics;

    @Captor
    private ArgumentCaptor<LongSupplier> sizeSupplierCaptor;

    @Captor
    private ArgumentCaptor<LongSupplier> evictionsSupplierCaptor;

    @Captor
    private ArgumentCaptor<Handler<Long>> periodicHandlerCaptor;

    @Test
    public void constructorShouldThrowWhenStoreIsNull() {
        // when and then
        assertThatNullPointerException()
                .isThrownBy(() -> new RedisStatsReporter(null, vertx, metrics, 1000L));
    }

    @Test
    public void constructorShouldThrowWhenVertxIsNull() {
        // when and then
        assertThatNullPointerException()
                .isThrownBy(() -> new RedisStatsReporter(store, null, metrics, 1000L));
    }

    @Test
    public void constructorShouldRegisterGaugesThatReadTheLatestPolledValues() {
        // given
        when(store.dbSize()).thenReturn(Future.succeededFuture(7L));
        when(store.evictedKeys()).thenReturn(Future.succeededFuture(3L));

        // when
        new RedisStatsReporter(store, vertx, metrics, 1000L).start();

        // then
        verify(metrics).registerL2Gauges(sizeSupplierCaptor.capture(), evictionsSupplierCaptor.capture());
        assertThat(sizeSupplierCaptor.getValue().getAsLong()).isEqualTo(7L);
        assertThat(evictionsSupplierCaptor.getValue().getAsLong()).isEqualTo(3L);
    }

    @Test
    public void startShouldPollImmediatelyAndScheduleAPeriodicPoll() {
        // given
        when(store.dbSize()).thenReturn(Future.succeededFuture(1L));
        when(store.evictedKeys()).thenReturn(Future.succeededFuture(1L));

        // when
        new RedisStatsReporter(store, vertx, metrics, 1000L).start();

        // then
        verify(store).dbSize();
        verify(store).evictedKeys();
        verify(vertx).setPeriodic(eq(1000L), any());
    }

    @Test
    public void startShouldReturnSelfForFluentWiring() {
        // given
        when(store.dbSize()).thenReturn(Future.succeededFuture(0L));
        when(store.evictedKeys()).thenReturn(Future.succeededFuture(0L));
        final RedisStatsReporter target = new RedisStatsReporter(store, vertx, metrics, 1000L);

        // when and then
        assertThat(target.start()).isSameAs(target);
    }

    @Test
    public void periodicTickShouldPollAgain() {
        // given
        when(store.dbSize()).thenReturn(Future.succeededFuture(1L));
        when(store.evictedKeys()).thenReturn(Future.succeededFuture(1L));
        when(vertx.setPeriodic(eq(1000L), periodicHandlerCaptor.capture())).thenReturn(1L);

        new RedisStatsReporter(store, vertx, metrics, 1000L).start();

        // when
        periodicHandlerCaptor.getValue().handle(1L);

        // then
        verify(store, times(2)).dbSize();
        verify(store, times(2)).evictedKeys();
    }

    @Test
    public void pollShouldLeaveGaugesAtZeroWhenBothProbesFail() {
        // given
        lenient().when(vertx.setPeriodic(eq(1000L), any())).thenReturn(1L);
        when(store.dbSize()).thenReturn(Future.failedFuture(new RuntimeException("dbsize down")));
        when(store.evictedKeys()).thenReturn(Future.failedFuture(new RuntimeException("info down")));

        // when
        new RedisStatsReporter(store, vertx, metrics, 1000L).start();

        // then
        verify(metrics).registerL2Gauges(sizeSupplierCaptor.capture(), evictionsSupplierCaptor.capture());
        assertThat(sizeSupplierCaptor.getValue().getAsLong()).isZero();
        assertThat(evictionsSupplierCaptor.getValue().getAsLong()).isZero();
    }
}
