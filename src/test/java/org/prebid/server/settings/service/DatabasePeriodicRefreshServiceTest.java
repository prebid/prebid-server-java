package org.prebid.server.settings.service;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;
import org.prebid.server.execution.timeout.TimeoutFactory;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.CacheNotificationListener;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.vertx.database.DatabaseClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class DatabasePeriodicRefreshServiceTest {

    @Mock
    private CacheNotificationListener cacheNotificationListener;
    @Mock
    private Vertx vertx;
    @Mock(strictness = LENIENT)
    private DatabaseClient databaseClient;
    private final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    private final TimeoutFactory timeoutFactory = new TimeoutFactory(clock);
    @Mock
    private Metrics metrics;

    private final Map<String, String> expectedRequests = singletonMap("id1", "value1");
    private final Map<String, String> expectedImps = singletonMap("id2", "value2");

    @BeforeEach
    public void setUp() {
        final StoredDataResult initialResult = StoredDataResult.of(singletonMap("id1", "value1"),
                singletonMap("id2", "value2"), emptyList());
        final StoredDataResult updateResult = StoredDataResult.of(singletonMap("id1", "null"),
                singletonMap("id2", "changed_value"), emptyList());

        given(databaseClient.executeQuery(eq("init_query"), anyList(), any(), any()))
                .willReturn(Future.succeededFuture(initialResult));
        given(databaseClient.executeQuery(eq("update_query"), anyList(), any(), any()))
                .willReturn(Future.succeededFuture(updateResult));
    }

    @Test
    public void shouldCallSaveWithExpectedParameters() {
        // when
        createAndInitService(1000);

        // then
        verify(cacheNotificationListener).save(expectedRequests, expectedImps);
    }

    @Test
    public void shouldCallInvalidateAndSaveWithExpectedParameters() {
        // given
        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(1L));

        // when
        createAndInitService(1000);

        // then
        verify(cacheNotificationListener).save(expectedRequests, expectedImps);
        verify(cacheNotificationListener).invalidate(singletonList("id1"), emptyList());
        verify(cacheNotificationListener).save(emptyMap(), singletonMap("id2", "changed_value"));
    }

    @Test
    public void initializeShouldMakeOneInitialRequestAndTwoScheduledRequestsWithParam() {
        // given
        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(1L, 2L));

        // when
        createAndInitService(1000);

        // then
        verify(databaseClient).executeQuery(eq("init_query"), eq(emptyList()), any(), any());
        verify(databaseClient, times(2)).executeQuery(eq("update_query"), anyList(), any(), any());
    }

    @Test
    public void initializeShouldMakeOnlyOneInitialRequestIfRefreshPeriodIsNegative() {
        // when
        createAndInitService(-1);

        // then
        verify(vertx, never()).setPeriodic(anyLong(), any());
        verify(databaseClient).executeQuery(anyString(), anyList(), any(), any());
    }

    @Test
    public void shouldUpdateTimerMetric() {
        // when
        createAndInitService(1000);

        // then
        verify(metrics).updateSettingsCacheRefreshTime(
                eq(MetricName.stored_request), eq(MetricName.initialize), anyLong());
    }

    @Test
    public void shouldUpdateTimerAndErrorMetric() {
        // given
        given(databaseClient.executeQuery(eq("init_query"), anyList(), any(), any()))
                .willReturn(Future.failedFuture("Query error"));

        // when
        createAndInitService(1000);

        // then
        verify(metrics).updateSettingsCacheRefreshTime(
                eq(MetricName.stored_request), eq(MetricName.initialize), anyLong());
        verify(metrics).updateSettingsCacheRefreshErrorMetric(
                eq(MetricName.stored_request), eq(MetricName.initialize));
    }

    private void createAndInitService(long refresh) {

        final DatabasePeriodicRefreshService databasePeriodicRefreshService = new DatabasePeriodicRefreshService(
                "init_query",
                "update_query",
                refresh,
                2000,
                MetricName.stored_request,
                cacheNotificationListener,
                vertx,
                databaseClient,
                timeoutFactory,
                metrics,
                clock);

        databasePeriodicRefreshService.initialize(Promise.promise());
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T... objects) {
        return inv -> {
            // invoking handler right away passing mock to it
            for (T obj : objects) {
                ((Handler<T>) inv.getArgument(1)).handle(obj);
            }
            return 0L;
        };
    }
}
