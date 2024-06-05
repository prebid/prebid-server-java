package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest.pipeline;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFile;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFileUpdate;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.PerformanceConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PipelineBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class PipelineUpdateConfiguratorTest {
    private static void applyUpdateOptions(
            DeviceDetectionOnPremisePipelineBuilder pipelineBuilder,
            DataFileUpdate dataFileUpdate) throws Exception {
        final ModuleConfig moduleConfig = new ModuleConfig();
        moduleConfig.setDataFile(new DataFile());
        moduleConfig.getDataFile().setUpdate(dataFileUpdate);
        moduleConfig.setPerformance(new PerformanceConfig());
        new PipelineBuilder(pipelineBuilder).build(moduleConfig);
    }

    @Test
    public void shouldIgnoreEmptyUrl() throws Exception {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);

        final DataFileUpdate config = new DataFileUpdate();
        config.setUrl("");

        // when
        applyUpdateOptions(builder, config);

        // then
        verify(builder, never()).setPerformanceProfile(any());
    }

    @Test
    public void shouldAssignURL() throws Exception {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);

        final DataFileUpdate config = new DataFileUpdate();
        config.setUrl("http://void/");

        final ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

        // when
        applyUpdateOptions(builder, config);

        // then
        verify(builder).setDataUpdateUrl(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getUrl());
    }

    @Test
    public void shouldIgnoreEmptyKey() throws Exception {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);

        final DataFileUpdate config = new DataFileUpdate();
        config.setLicenseKey("");

        // when
        applyUpdateOptions(builder, config);

        // then
        verify(builder, never()).setDataUpdateLicenseKey(any());
    }

    @Test
    public void shouldAssignKey() throws Exception {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);

        final DataFileUpdate config = new DataFileUpdate();
        config.setLicenseKey("687-398475-34876-384678-34756-3487");

        final ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

        // when
        applyUpdateOptions(builder, config);

        // then
        verify(builder).setDataUpdateLicenseKey(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getLicenseKey());
    }

    @Test
    public void shouldAssignAuto() throws Exception {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);

        final DataFileUpdate config = new DataFileUpdate();
        config.setAuto(true);

        final ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);

        // when
        applyUpdateOptions(builder, config);

        // then
        verify(builder).setAutoUpdate(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getAuto());
    }

    @Test
    public void shouldAssignOnStartup() throws Exception {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);

        final DataFileUpdate config = new DataFileUpdate();
        config.setOnStartup(true);

        final ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);

        // when
        applyUpdateOptions(builder, config);

        // then
        verify(builder).setDataUpdateOnStartup(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getOnStartup());
    }

    @Test
    public void shouldAssignWatchFileSystem() throws Exception {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);

        final DataFileUpdate config = new DataFileUpdate();
        config.setWatchFileSystem(true);

        final ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);

        // when
        applyUpdateOptions(builder, config);

        // then
        verify(builder).setDataFileSystemWatcher(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getWatchFileSystem());
    }

    @Test
    public void shouldAssignPollingInterval() throws Exception {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);

        final DataFileUpdate config = new DataFileUpdate();
        config.setPollingInterval(643);

        final ArgumentCaptor<Integer> argumentCaptor = ArgumentCaptor.forClass(Integer.class);

        // when
        applyUpdateOptions(builder, config);

        // then
        verify(builder).setUpdatePollingInterval(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getPollingInterval());
    }
}
