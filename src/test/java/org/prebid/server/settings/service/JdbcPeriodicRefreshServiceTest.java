package org.prebid.server.settings.service;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.settings.CacheNotificationListener;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.vertx.jdbc.JdbcClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class JdbcPeriodicRefreshServiceTest {

    private static TimeoutFactory timeoutFactory = new TimeoutFactory(Clock.fixed(Instant.now(), ZoneId.systemDefault()));

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private CacheNotificationListener cacheNotificationListener;

    @Mock
    private JdbcClient jdbcClient;

    @Mock
    private Vertx vertx;

    private Map<String, String> expectedRequests = singletonMap("id1", "value1");
    private Map<String, String> expectedImps = singletonMap("id2", "value2");

    @Before
    public void setUp() {
        final StoredDataResult initialResult = StoredDataResult.of(singletonMap("id1", "value1"),
                singletonMap("id2", "value2"), emptyList());
        final StoredDataResult updateResult = StoredDataResult.of(singletonMap("id1", "null"),
                singletonMap("id2", "changed_value"), emptyList());

        given(jdbcClient.executeQuery(eq("init_query"), anyList(), any(), any()))
                .willReturn(Future.succeededFuture(initialResult));
        given(jdbcClient.executeQuery(eq("update_query"), anyList(), any(), any()))
                .willReturn(Future.succeededFuture(updateResult));
    }

    @Test
    public void creationShouldFailOnNullArgumentsAndBlankQuery() {
        assertThatNullPointerException().isThrownBy(() -> createAndInitService(
                null, null, null, 0, null, null, null, 0));
        assertThatNullPointerException().isThrownBy(() -> createAndInitService(
                cacheNotificationListener, null, null, 0, null, null, null, 0));
        assertThatNullPointerException().isThrownBy(() -> createAndInitService(
                cacheNotificationListener, vertx, null, 0, null, null, null, 0));
        assertThatNullPointerException().isThrownBy(() -> createAndInitService(
                cacheNotificationListener, vertx, jdbcClient, 0, null, null, null, 0));
        assertThatNullPointerException().isThrownBy(() -> createAndInitService(
                cacheNotificationListener, vertx, jdbcClient, 0, "init_query", null, null, 0));
        assertThatNullPointerException().isThrownBy(() -> createAndInitService(
                cacheNotificationListener, vertx, jdbcClient, 0, "init_query", "update_query", null, 0));
        assertThatNullPointerException().isThrownBy(() -> createAndInitService(
                cacheNotificationListener, vertx, jdbcClient, 0, "  ", null, timeoutFactory, 0));
        assertThatNullPointerException().isThrownBy(() -> createAndInitService(
                cacheNotificationListener, vertx, jdbcClient, 0, "init_query", " ", timeoutFactory, 0));
    }

    @Test
    public void shouldCallSaveWithExpectedParameters() {
        // when
        createAndInitService(cacheNotificationListener, vertx, jdbcClient, 1000,
                "init_query", "update_query", timeoutFactory, 2000);

        // then
        verify(cacheNotificationListener).save(expectedRequests, expectedImps);
    }

    @Test
    public void shouldCallInvalidateAndSaveWithExpectedParameters() {
        // given
        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(1L));

        // when
        createAndInitService(cacheNotificationListener, vertx, jdbcClient, 1000,
                "init_query", "update_query", timeoutFactory, 2000);

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
        createAndInitService(cacheNotificationListener, vertx, jdbcClient, 1000,
                "init_query", "update_query", timeoutFactory, 2000);

        // then
        verify(jdbcClient).executeQuery(eq("init_query"), eq(emptyList()), any(), any());
        verify(jdbcClient, times(2)).executeQuery(eq("update_query"), anyList(), any(), any());
    }

    @Test
    public void initializeShouldMakeOnlyOneInitialRequestIfRefreshPeriodIsNegative() {
        // when
        createAndInitService(cacheNotificationListener, vertx, jdbcClient, -1,
                "init_query", "update_query", timeoutFactory, 2000);

        // then
        verify(vertx, never()).setPeriodic(anyLong(), any());
        verify(jdbcClient).executeQuery(anyString(), anyList(), any(), any());
    }

    private static void createAndInitService(CacheNotificationListener cacheNotificationListener,
                                             Vertx vertx, JdbcClient jdbcClient, long refresh,
                                             String query, String updateQuery,
                                             TimeoutFactory timeoutFactory, long timeout) {
        final JdbcPeriodicRefreshService jdbcPeriodicRefreshService =
                new JdbcPeriodicRefreshService(cacheNotificationListener, vertx, jdbcClient, refresh,
                        query, updateQuery, timeoutFactory, timeout);
        jdbcPeriodicRefreshService.initialize();
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
