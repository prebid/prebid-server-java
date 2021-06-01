package org.prebid.server.log;

import io.vertx.core.logging.Logger;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class ConditionalLoggerFactoryTest {

    @Test
    public void shouldCreateLogger() {
        // when
        final ConditionalLogger logger = ConditionalLoggerFactory.getOrCreate(mock(Logger.class));

        // then
        assertThat(logger).isNotNull();
    }

    @Test
    public void shouldCreateUniqueLoggers() {
        // when
        final ConditionalLogger logger1 = ConditionalLoggerFactory.getOrCreate(mock(Logger.class));
        final ConditionalLogger logger2 = ConditionalLoggerFactory.getOrCreate(mock(Logger.class));

        // then
        assertThat(logger1).isNotEqualTo(logger2);
    }

    @Test
    public void shouldReturnExistingLogger() {
        // given
        final Logger logger = mock(Logger.class);

        // when
        final ConditionalLogger logger1 = ConditionalLoggerFactory.getOrCreate(logger);
        final ConditionalLogger logger2 = ConditionalLoggerFactory.getOrCreate(logger);

        // then
        assertThat(logger1).isEqualTo(logger2);
    }
}
