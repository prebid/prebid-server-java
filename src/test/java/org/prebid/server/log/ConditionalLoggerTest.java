package org.prebid.server.log;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@ExtendWith(MockitoExtension.class)
@ExtendWith(VertxExtension.class)
@RunWith(VertxUnitRunner.class)
public class ConditionalLoggerTest {

    @Mock
    private Logger logger;

    private Vertx vertx;

    private ConditionalLogger conditionalLogger;

    @BeforeEach
    public void setUp() {
        vertx = Vertx.vertx();
        conditionalLogger = new ConditionalLogger(logger);
    }

    @AfterEach
    public void tearDown(VertxTestContext context) {
        vertx.close(context.succeedingThenComplete());
    }

    @Test
    public void infoWithKeyShouldCallLoggerWithExpectedCount() {
        // when
        for (int i = 0; i < 10; i++) {
            conditionalLogger.infoWithKey("key", "Log Message1", 2);
            conditionalLogger.infoWithKey("key", "Log Message2", 2);
        }

        // then
        verify(logger, times(10)).info("Log Message2");
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void infoShouldCallLoggerWithExpectedCount() {
        // when
        for (int i = 0; i < 10; i++) {
            conditionalLogger.info("Log Message", 2);
        }

        // then
        verify(logger, times(5)).info("Log Message");
    }

    @Test
    public void infoShouldCallLoggerBySpecifiedKeyWithExpectedCount() {
        // given
        conditionalLogger = new ConditionalLogger("key1", logger);

        // when
        for (int i = 0; i < 10; i++) {
            conditionalLogger.info("Log Message" + i, 2);
        }

        // then
        verify(logger, times(5)).info(argThat(o -> o.toString().startsWith("Log Message")));
    }

    @Test
    public void infoShouldCallLoggerWithExpectedTimeout() {
        // when
        for (int i = 0; i < 5; i++) {
            conditionalLogger.info("Log Message", 200, TimeUnit.MILLISECONDS);
            doWait(100);
        }

        // then
        verify(logger, times(2)).info("Log Message");
    }

    @Test
    public void infoShouldCallLoggerBySpecifiedKeyWithExpectedTimeout() {
        // given
        conditionalLogger = new ConditionalLogger("key1", logger);

        // when
        for (int i = 0; i < 5; i++) {
            conditionalLogger.info("Log Message" + i, 200, TimeUnit.MILLISECONDS);
            doWait(100);
        }

        // then
        verify(logger, times(2)).info(argThat(o -> o.toString().startsWith("Log Message")));
    }

    private void doWait(long timeout) {
        final Promise<?> promise = Promise.promise();
        vertx.setTimer(timeout, id -> promise.complete());
        promise.future().toCompletionStage().toCompletableFuture().join();
    }
}
