package org.prebid.server.log;

import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@ExtendWith(VertxExtension.class)
public class ConditionalLoggerTest {

    @Spy
    private Logger logger = LoggerFactory.getLogger(ConditionalLoggerTest.class);

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
    public void infoShouldCallLoggerForEachLog() {
        // when
        for (int i = 0; i < 5; i++) {
            conditionalLogger.info("Log Message" + i, 0, TimeUnit.MILLISECONDS);
        }

        // then
        verify(logger, times(5)).info(argThat(o -> o.toString().startsWith("Log Message")));
    }

    @Test
    public void infoShouldSkipLogsForDuration() {
        // when
        for (int i = 0; i < 5; i++) {
            conditionalLogger.info("Log Message", 200, TimeUnit.MILLISECONDS);
            doWait(100);
        }

        // then
        verify(logger, times(2)).info("Log Message");
    }

    @Test
    public void infoShouldSkipLogsForKeyForDuration() {
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
