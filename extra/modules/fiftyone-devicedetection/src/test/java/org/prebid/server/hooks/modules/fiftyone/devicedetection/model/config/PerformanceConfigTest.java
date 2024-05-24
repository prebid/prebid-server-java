package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config;

import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PerformanceConfigTest {
    @Test
    public void shouldReturnProfile() {

        // given
        final String profile = "TurtleSlow";

        // when
        final PerformanceConfig performanceConfig = new PerformanceConfig();
        performanceConfig.setProfile(profile);

        // then
        assertThat(performanceConfig.getProfile()).isEqualTo(profile);
    }

    @Test
    public void shouldReturnConcurrency() {

        // given
        final int concurrency = 5438;

        // when
        final PerformanceConfig performanceConfig = new PerformanceConfig();
        performanceConfig.setConcurrency(concurrency);

        // then
        assertThat(performanceConfig.getConcurrency()).isEqualTo(concurrency);
    }

    @Test
    public void shouldReturnDifference() {

        // given
        final int difference = 5438;

        // when
        final PerformanceConfig performanceConfig = new PerformanceConfig();
        performanceConfig.setDifference(difference);

        // then
        assertThat(performanceConfig.getDifference()).isEqualTo(difference);
    }

    @Test
    public void shouldReturnAllowUnmatched() {

        // given
        final boolean allowUnmatched = true;

        // when
        final PerformanceConfig performanceConfig = new PerformanceConfig();
        performanceConfig.setAllowUnmatched(allowUnmatched);

        // then
        assertThat(performanceConfig.getAllowUnmatched()).isEqualTo(allowUnmatched);
    }

    @Test
    public void shouldReturnDrift() {

        // given
        final int drift = 8624;

        // when
        final PerformanceConfig performanceConfig = new PerformanceConfig();
        performanceConfig.setDrift(drift);

        // then
        assertThat(performanceConfig.getDrift()).isEqualTo(drift);
    }

    @Test
    public void shouldHaveDescription() {

        // given and when
        final PerformanceConfig performanceConfig = new PerformanceConfig();
        performanceConfig.setProfile("LightningFast");

        // when and then
        assertThat(performanceConfig.toString()).isNotBlank();
    }
}
