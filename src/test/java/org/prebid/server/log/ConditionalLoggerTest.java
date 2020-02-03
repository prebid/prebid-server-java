package org.prebid.server.log;

import io.vertx.core.logging.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class ConditionalLoggerTest {

    @Mock
    private Logger logger;

    private ConditionalLogger conditionalLogger;

    @Before
    public void setUp() throws Exception {
        conditionalLogger = new ConditionalLogger(logger);
    }

    @Test
    public void log() {
        //when
        for (int i = 0; i < 100; i++) {
            conditionalLogger.info("Hello", 20);
        }
        //then
        verify(logger, times(5)).info(any());
    }

}
