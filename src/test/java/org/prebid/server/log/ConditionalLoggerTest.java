package org.prebid.server.log;

import io.vertx.core.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ConditionalLoggerTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Logger logger;

    private ConditionalLogger conditionalLogger;

    @Before
    public void setUp() throws Exception {
        conditionalLogger = new ConditionalLogger(logger);
    }

    @Test
    public void log() {
        // when
        for (int i = 0; i < 100; i++) {
            conditionalLogger.info("Hello", 20);
        }
        // then
        verify(logger, times(5)).info(any());
    }
}
