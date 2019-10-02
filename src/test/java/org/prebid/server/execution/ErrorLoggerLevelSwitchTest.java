package org.prebid.server.execution;

import org.junit.Before;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ErrorLoggerLevelSwitchTest {

    private LoggerLevelModifier errorLoggerLevelSwitch;

    @Before
    public void setUp() {
        errorLoggerLevelSwitch = new LoggerLevelModifier();
    }

    @Test
    public void shouldReturnFalseWhenErrorLevelCountIsNotSet() {
        // given and when
        final boolean result = errorLoggerLevelSwitch.isLogLevelError();

        //then
        assertThat(result).isFalse();
    }

    @Test
    public void shouldReturnFalseWhenErrorLevelCountIsZero() {
        // given
        errorLoggerLevelSwitch.setErrorOnBadRequestCount(0);

        // when
        final boolean result = errorLoggerLevelSwitch.isLogLevelError();

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void shouldReturnFalseWhenErrorLevelCountIsNegative() {
        // given
        errorLoggerLevelSwitch.setErrorOnBadRequestCount(-123);

        // when
        final boolean result = errorLoggerLevelSwitch.isLogLevelError();

        // then
        assertThat(result).isFalse();
    }

    @Test
    public void shouldReturnTrueWhenErrorLevelCountIsPositive() {
        // given
        errorLoggerLevelSwitch.setErrorOnBadRequestCount(123);

        // when
        final boolean result = errorLoggerLevelSwitch.isLogLevelError();

        // then
        assertThat(result).isTrue();
    }
}

