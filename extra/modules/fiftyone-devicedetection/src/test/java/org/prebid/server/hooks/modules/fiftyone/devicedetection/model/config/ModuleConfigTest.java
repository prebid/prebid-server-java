package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config;

import org.junit.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ModuleConfigTest {
    @Test
    public void shouldReturnAccountFilter() {

        // given
        final AccountFilter accountFilter = new AccountFilter();
        accountFilter.setAllowList(Collections.singletonList("raccoon"));

        // when
        final ModuleConfig moduleConfig = new ModuleConfig();
        moduleConfig.setAccountFilter(accountFilter);

        // then
        assertThat(moduleConfig.getAccountFilter()).isEqualTo(accountFilter);
    }

    @Test
    public void shouldReturnDataFile() {

        // given
        final DataFile dataFile = new DataFile();
        dataFile.setPath("B:\\archive");

        // when
        final ModuleConfig moduleConfig = new ModuleConfig();
        moduleConfig.setDataFile(dataFile);

        // then
        assertThat(moduleConfig.getDataFile()).isEqualTo(dataFile);
    }

    @Test
    public void shouldReturnPerformanceConfig() {

        // given
        final PerformanceConfig performanceConfig = new PerformanceConfig();
        performanceConfig.setProfile("SilentHunter");

        // when
        final ModuleConfig moduleConfig = new ModuleConfig();
        moduleConfig.setPerformance(performanceConfig);

        // then
        assertThat(moduleConfig.getPerformance()).isEqualTo(performanceConfig);
    }

    @Test
    public void shouldHaveDescription() {

        // given
        final DataFile dataFile = new DataFile();
        dataFile.setPath("Z:\\virtual-drive");

        // when
        final ModuleConfig moduleConfig = new ModuleConfig();
        moduleConfig.setDataFile(dataFile);

        // when and then
        assertThat(moduleConfig.toString()).isNotBlank();
    }
}
