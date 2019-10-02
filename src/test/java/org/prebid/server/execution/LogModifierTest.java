package org.prebid.server.execution;

import io.vertx.core.logging.Logger;
import org.junit.Before;
import org.junit.Test;

import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;

public class LogModifierTest {

    private final static BiConsumer<Logger, String> DEFAULT_LOG_MODIFIER = Logger::info;
    private LogModifier errorLoggerLevelSwitch;

    @Before
    public void setUp() {
        errorLoggerLevelSwitch = new LogModifier(DEFAULT_LOG_MODIFIER);
    }

    @Test
    public void shouldReturnDefaultLogModifierWhenErrorLevelCountIsNotSet() {
        // given and when
        final BiConsumer<Logger, String> logModifier = errorLoggerLevelSwitch.getLogModifier();

        // then
        assertThat(logModifier).isEqualTo(DEFAULT_LOG_MODIFIER);
    }

    @Test
    public void shouldReturnDefaultLogModifierWhenErrorLevelCountIsZero() {
        // given
        errorLoggerLevelSwitch.setLogModifier(0, Logger::error);

        // when and then
        assertThat(errorLoggerLevelSwitch.getLogModifier()).isEqualTo(DEFAULT_LOG_MODIFIER);
    }

    @Test
    public void shouldReturnDefaultLogModifierWhenErrorLevelCountIsNegative() {
        // given
        errorLoggerLevelSwitch.setLogModifier(-123, Logger::error);

        // when and then
        assertThat(errorLoggerLevelSwitch.getLogModifier()).isEqualTo(DEFAULT_LOG_MODIFIER);
    }

    @Test
    public void shouldReturnLogModifierWhenErrorLevelCountIsPositive() {
        // given
        final BiConsumer<Logger, String> logModifier = Logger::error;
        errorLoggerLevelSwitch.setLogModifier(123, logModifier);

        // when
        final BiConsumer<Logger, String> result = errorLoggerLevelSwitch.getLogModifier();

        // then
        assertThat(result).isEqualTo(logModifier);
    }
}

