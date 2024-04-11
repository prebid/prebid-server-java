package org.prebid.server.log;

import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@RunWith(VertxUnitRunner.class)
public class ConditionalLoggerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Logger logger;

    private Vertx vertx;

    private ConditionalLogger conditionalLogger;

    @Before
    public void setUp() {
        vertx = Vertx.vertx();
        conditionalLogger = new ConditionalLogger(logger);
    }

    @After
    public void tearDown(TestContext context) {
        vertx.close(context.asyncAssertSuccess());
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
    public void infoShouldCallLoggerWithExpectedTimeout(TestContext context) {
        // when
        for (int i = 0; i < 5; i++) {
            conditionalLogger.info("Log Message", 200, TimeUnit.MILLISECONDS);
            doWait(context, 100);
        }

        // then
        verify(logger, times(2)).info("Log Message");
    }

    @Test
    public void infoShouldCallLoggerBySpecifiedKeyWithExpectedTimeout(TestContext context) {
        // given
        conditionalLogger = new ConditionalLogger("key1", logger);

        // when
        for (int i = 0; i < 5; i++) {
            conditionalLogger.info("Log Message" + i, 200, TimeUnit.MILLISECONDS);
            doWait(context, 100);
        }

        // then
        verify(logger, times(2)).info(argThat(o -> o.toString().startsWith("Log Message")));
    }

    private void doWait(TestContext context, long timeout) {
        final Async async = context.async();
        vertx.setTimer(timeout, id -> async.complete());
        async.await();
    }
}
