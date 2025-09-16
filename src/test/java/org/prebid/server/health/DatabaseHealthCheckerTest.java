package org.prebid.server.health;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.sqlclient.Pool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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

@ExtendWith(MockitoExtension.class)
public class DatabaseHealthCheckerTest {

    private static final String DATABASE_CHECK_NAME = "database";
    private static final long TEST_REFRESH_PERIOD = 1000;
    private static final String TEST_TIME_STRING = ZonedDateTime.now(Clock.systemUTC()).toString();

    @Mock
    private Vertx vertx;
    @Mock
    private Pool pool;

    private DatabaseHealthChecker target;

    @BeforeEach
    public void setUp() {
        target = new DatabaseHealthChecker(vertx, pool, TEST_REFRESH_PERIOD);
    }

    @Test
    public void creationShouldFailWithNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> new DatabaseHealthChecker(null, pool, 0));
        assertThatNullPointerException().isThrownBy(() -> new DatabaseHealthChecker(vertx, null, TEST_REFRESH_PERIOD));
    }

    @Test
    public void creationShouldFailWhenRefreshPeriodIsZeroOrNegative() {
        assertThatIllegalArgumentException().isThrownBy(() -> new DatabaseHealthChecker(vertx, pool, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> new DatabaseHealthChecker(vertx, pool, -1));
    }

    @Test
    public void getCheckNameShouldReturnExpectedResult() {
        assertThat(target.name()).isEqualTo(DATABASE_CHECK_NAME);
    }

    @Test
    public void getLastStatusShouldReturnNullStatusIfCheckWasNotInitialized() {
        assertThat(target.status()).isNull();
    }

    @Test
    public void getLastStatusShouldReturnStatusUpAndLastUpdatedAfterTestTime() {
        // given
        given(pool.getConnection()).willReturn(Future.succeededFuture());

        // when
        target.updateStatus();

        // then
        final StatusResponse lastStatus = target.status();
        assertThat(lastStatus.getStatus()).isEqualTo("UP");
        assertThat(lastStatus.getLastUpdated()).isAfter(TEST_TIME_STRING);
    }

    @Test
    public void getLastStatusShouldReturnStatusDownAndLastUpdatedAfterTestTime() {
        // given
        given(pool.getConnection()).willReturn(Future.failedFuture("fail"));

        // when
        target.updateStatus();

        // then
        final StatusResponse lastStatus = target.status();
        assertThat(lastStatus.getStatus()).isEqualTo("DOWN");
        assertThat(lastStatus.getLastUpdated()).isAfter(TEST_TIME_STRING);
    }

    @Test
    public void initializeShouldMakeOneInitialRequestAndTwoScheduledRequests() {
        // given
        given(vertx.setPeriodic(anyLong(), any())).willAnswer(inv -> {
            final Handler<Long> handler = inv.getArgument(1);
            handler.handle(1L);
            handler.handle(2L);
            return 0L;
        });
        given(pool.getConnection()).willReturn(Future.succeededFuture());

        // when
        target.initialize();

        // then
        verify(pool, times(3)).getConnection();
    }
}
