package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.pipelinebuilders;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.MergingConfigurator;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.config.DataFileUpdate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class PipelineUpdateConfiguratorTest {
    @Test
    public void shouldNotCrashWithNoConfig() {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final MergingConfigurator<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = new PipelineUpdateConfigurator();

        // when
        configurator.applyProperties(builder, null);

        // then
        verify(builder, times(1)).setDataUpdateService(any());
    }

    @Test
    public void shouldIgnoreEmptyUrl() {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final MergingConfigurator<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = new PipelineUpdateConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setUrl("");

        // when
        configurator.applyProperties(builder, config);

        // then
        verify(builder, never()).setPerformanceProfile(any());
    }

    @Test
    public void shouldAssignURL() {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final MergingConfigurator<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = new PipelineUpdateConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setUrl("http://void/");

        final ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

        // when
        configurator.applyProperties(builder, config);

        // then
        verify(builder).setDataUpdateUrl(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getUrl());
    }

    @Test
    public void shouldIgnoreEmptyKey() {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final MergingConfigurator<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = new PipelineUpdateConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setLicenseKey("");

        // when
        configurator.applyProperties(builder, config);

        // then
        verify(builder, never()).setDataUpdateLicenseKey(any());
    }

    @Test
    public void shouldAssignKey() {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final MergingConfigurator<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = new PipelineUpdateConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setLicenseKey("687-398475-34876-384678-34756-3487");

        final ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

        // when
        configurator.applyProperties(builder, config);

        // then
        verify(builder).setDataUpdateLicenseKey(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getLicenseKey());
    }

    @Test
    public void shouldAssignAuto() {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final MergingConfigurator<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = new PipelineUpdateConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setAuto(true);

        final ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);

        // when
        configurator.applyProperties(builder, config);

        // then
        verify(builder).setAutoUpdate(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getAuto());
    }

    @Test
    public void shouldAssignOnStartup() {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final MergingConfigurator<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = new PipelineUpdateConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setOnStartup(true);

        final ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);

        // when
        configurator.applyProperties(builder, config);

        // then
        verify(builder).setDataUpdateOnStartup(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getOnStartup());
    }

    @Test
    public void shouldAssignWatchFileSystem() {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final MergingConfigurator<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = new PipelineUpdateConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setWatchFileSystem(true);

        final ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);

        // when
        configurator.applyProperties(builder, config);

        // then
        verify(builder).setDataFileSystemWatcher(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getWatchFileSystem());
    }

    @Test
    public void shouldAssignPollingInterval() {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final MergingConfigurator<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = new PipelineUpdateConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setPollingInterval(643);

        final ArgumentCaptor<Integer> argumentCaptor = ArgumentCaptor.forClass(Integer.class);

        // when
        configurator.applyProperties(builder, config);

        // then
        verify(builder).setUpdatePollingInterval(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getPollingInterval());
    }
}
