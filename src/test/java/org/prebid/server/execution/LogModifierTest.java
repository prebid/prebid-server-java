package org.prebid.server.execution;

import io.vertx.core.logging.Logger;
import org.junit.Before;
import org.junit.Test;

import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

public class LogModifierTest {

    private final static BiConsumer<Logger, String> DEFAULT_LOG_MODIFIER = Logger::info;
    private LogModifier logModifier;

    @Before
    public void setUp() {
        logModifier = new LogModifier(DEFAULT_LOG_MODIFIER);
    }

    @Test
    public void shouldReturnDefaultLogModifierWhenNothingWasSet() {
        // given and when
        final BiConsumer<Logger, String> defaultLogModifier = logModifier.get();

        // then
        assertThat(defaultLogModifier).isEqualTo(DEFAULT_LOG_MODIFIER);
    }

    @Test
    public void shouldReturnDefaultLogModifierWhenErrorLevelCountIsZero() {
        // given
        logModifier.set(Logger::error, 0);

        // when and then
        assertThat(logModifier.get()).isEqualTo(DEFAULT_LOG_MODIFIER);
    }

    @Test
    public void shouldReturnDefaultLogModifierWhenErrorLevelCountIsNegative() {
        // given
        logModifier.set(Logger::error, -123);

        // when and then
        assertThat(logModifier.get()).isEqualTo(DEFAULT_LOG_MODIFIER);
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

