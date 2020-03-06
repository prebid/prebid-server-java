package org.prebid.server.log;

import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
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

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    public void infoShouldCallLoggerWithExpectedCount() {
        // when
        for (int i = 0; i < 100; i++) {
            conditionalLogger.info("Log Message", 20);
        }
        // then
        verify(logger, times(5)).info("Log Message");
    }

    @Test
    public void infoShouldCallLoggerWithExpectedTimeout(TestContext context) {
        // when
        for (int i = 0; i < 5; i++) {
            conditionalLogger.info("Log Message", 1, TimeUnit.SECONDS);
            doWait(context, 500);
        }
        // then
        verify(logger, times(2)).info("Log Message");
    }

    private void doWait(TestContext context, long timeout) {
        final Async async = context.async();
        vertx.setTimer(timeout, id -> async.complete());
        async.await();
    }
}
