package org.prebid.server.log;

import io.vertx.core.logging.Logger;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
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

public class ConditionalLoggerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Logger logger;

    private ConditionalLogger conditionalLogger;

    @Before
    public void setUp() {
        conditionalLogger = new ConditionalLogger(logger);
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
    public void infoShouldCallLoggerWithExpectedTimeout() throws InterruptedException {
        // when
        for (int i = 0; i < 5; i++) {
            conditionalLogger.info("Log Message", 1, TimeUnit.SECONDS);
            Thread.sleep(500);
        }
        // then
        verify(logger, times(2)).info("Log Message");
    }
}
