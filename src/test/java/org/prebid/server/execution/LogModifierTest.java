package org.prebid.server.execution;

import io.vertx.core.logging.Logger;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class LogModifierTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Logger logger;

    private LogModifier logModifier;

    @Before
    public void setUp() {
        given(logger.isInfoEnabled()).willReturn(true);

        logModifier = new LogModifier(logger);
    }

    @Test
    public void shouldUseDefaultLogModifierWhenNothingWasSet() {
        // given and when
        logModifier.get().accept(logger, "test");

        // then
        verify(logger).info(any());
    }

    @Test
    public void shouldUseDefaultLogModifierWhenErrorLevelCountIsZero() {
        // given and when
        logModifier.set(Logger::error, 0);
        logModifier.get().accept(logger, "test");

        // then
        verify(logger).info(any());
    }

    @Test
    public void shouldUseDefaultLogModifierWhenErrorLevelCountIsNegative() {
        // given and when
        logModifier.set(Logger::error, -123);
        logModifier.get().accept(logger, "test");

        // then
        verify(logger).info(any());
    }

    @Test
    public void shouldReturnLogModifierWhenErrorLevelCountIsPositive() {
        // given
        final BiConsumer<Logger, String> loggingLevelModifier = Logger::error;
        logModifier.set(loggingLevelModifier, 123);

        // when
        final BiConsumer<Logger, String> result = logModifier.get();

        // then
        assertThat(result).isEqualTo(loggingLevelModifier);
    }
}

