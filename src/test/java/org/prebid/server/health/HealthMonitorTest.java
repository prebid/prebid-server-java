package org.prebid.server.health;

import org.junit.Before;
import org.junit.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class HealthMonitorTest {

    private HealthMonitor healthMonitor;

    @Before
    public void setUp() {
        healthMonitor = new HealthMonitor();
    }

    @Test
    public void calculateHealthIndexShouldReturnFullHealthIfNoRequestsSubmitted() {
        // when
        final BigDecimal result = healthMonitor.calculateHealthIndex();

        // then
        assertThat(result).isEqualTo(BigDecimal.ONE);
    }

    @Test
    public void calculateHealthIndexShouldReturnExpectedResult() {
        // when
        healthMonitor.incTotal();
        healthMonitor.incTotal();
        healthMonitor.incTotal();
        healthMonitor.incSuccess();

        final BigDecimal result = healthMonitor.calculateHealthIndex();

        // then
        assertThat(result).isEqualTo(new BigDecimal("0.33"));
    }

    @Test
    public void calculateHealthIndexShouldResetResult() {
        // when
        healthMonitor.incTotal();
        healthMonitor.incSuccess();
        healthMonitor.calculateHealthIndex();

        final BigDecimal result = healthMonitor.calculateHealthIndex();

        // then
        assertThat(result).isEqualTo(BigDecimal.ONE);
    }
}
