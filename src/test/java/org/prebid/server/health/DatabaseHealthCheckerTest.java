package org.prebid.server.health;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLClient;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.health.model.StatusResponse;

import java.time.Clock;
import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class DatabaseHealthCheckerTest {

    private static final String DATABASE_CHECK_NAME = "database";
    private static final long TEST_REFRESH_PERIOD = 1000;
    private static final String TEST_TIME_STRING = ZonedDateTime.now(Clock.systemUTC()).toString();

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Vertx vertx;
    @Mock
    private JDBCClient jdbcClient;
    @Mock
    private SQLClient sqlClient;

    private DatabaseHealthChecker databaseHealthCheck;

    @Before
    public void setUp() {
        databaseHealthCheck = new DatabaseHealthChecker(vertx, jdbcClient, TEST_REFRESH_PERIOD);
    }

    @Test
    public void creationShouldFailWithNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new DatabaseHealthChecker(null, jdbcClient, 0));
        assertThatNullPointerException().isThrownBy(() -> new DatabaseHealthChecker(vertx, null, TEST_REFRESH_PERIOD));
    }

    @Test
    public void creationShouldFailWhenRefreshPeriodIsZeroOrNegative() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DatabaseHealthChecker(vertx, jdbcClient, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new DatabaseHealthChecker(vertx, jdbcClient, -1));
    }

    @Test
    public void getCheckNameShouldReturnExpectedResult() {
        assertThat(databaseHealthCheck.name()).isEqualTo(DATABASE_CHECK_NAME);
    }

    @Test
    public void getLastStatusShouldReturnNullStatusIfCheckWasNotInitialized() {
        assertThat(databaseHealthCheck.status()).isNull();
    }

    @Test
    public void getLastStatusShouldReturnStatusUpAndLastUpdatedAfterTestTime() {
        // given
        given(jdbcClient.getConnection(any()))
                .willAnswer(withSelfAndPassObjectToHandler(0, sqlClient, Future.succeededFuture()));

        // when
        databaseHealthCheck.updateStatus();

        // then
        final StatusResponse lastStatus = databaseHealthCheck.status();
        assertThat(lastStatus.getStatus()).isEqualTo("UP");
        assertThat(lastStatus.getLastUpdated()).isAfter(TEST_TIME_STRING);
    }

    @Test
    public void getLastStatusShouldReturnStatusDownAndLastUpdatedAfterTestTime() {
        // given
        given(jdbcClient.getConnection(any()))
                .willAnswer(withSelfAndPassObjectToHandler(0, sqlClient, Future.failedFuture("fail")));

        // when
        databaseHealthCheck.updateStatus();

        // then
        final StatusResponse lastStatus = databaseHealthCheck.status();
        assertThat(lastStatus.getStatus()).isEqualTo("DOWN");
        assertThat(lastStatus.getLastUpdated()).isAfter(TEST_TIME_STRING);
    }

    @Test
    public void initializeShouldMakeOneInitialRequestAndTwoScheduledRequests() {
        // given
        given(vertx.setPeriodic(anyLong(), any()))
                .willAnswer(withSelfAndPassObjectToHandler(1, 0L, 1L, 2L));
        given(jdbcClient.getConnection(any()))
                .willAnswer(withSelfAndPassObjectToHandler(0, sqlClient, Future.succeededFuture()));

        // when
        databaseHealthCheck.initialize();

        // then
        verify(jdbcClient, times(3)).getConnection(any());
    }

    @SuppressWarnings("unchecked")
    private <T> Answer<Object> withSelfAndPassObjectToHandler(int arg, Object result, T... objects) {
        return inv -> {
            // invoking handler right away passing mock to it
            for (T obj : objects) {
                ((Handler<T>) inv.getArgument(arg)).handle(obj);
            }
            return result;
        };
    }
}