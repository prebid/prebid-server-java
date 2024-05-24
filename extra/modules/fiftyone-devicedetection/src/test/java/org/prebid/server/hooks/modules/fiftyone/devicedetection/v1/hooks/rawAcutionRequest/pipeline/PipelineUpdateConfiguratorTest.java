package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest.pipeline;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFileUpdate;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionRawAuctionRequestHook;

import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PipelineUpdateConfiguratorTest {
    private static BiConsumer<DeviceDetectionOnPremisePipelineBuilder,
            DataFileUpdate> makeConfigurator() throws Exception {

        return new FiftyOneDeviceDetectionRawAuctionRequestHook(null) {
            @Override
            protected DeviceDetectionOnPremisePipelineBuilder makeBuilder() throws Exception {

                final DeviceDetectionOnPremisePipelineBuilder builder
                        = mock(DeviceDetectionOnPremisePipelineBuilder.class);
                when(builder.build()).thenReturn(null);
                return builder;
            }

            @Override
            public void applyUpdateOptions(
                    DeviceDetectionOnPremisePipelineBuilder pipelineBuilder,
                    DataFileUpdate updateConfig) {

                super.applyUpdateOptions(pipelineBuilder, updateConfig);
            }
        }
            ::applyUpdateOptions;
    }

    @Test
    public void shouldIgnoreEmptyUrl() throws Exception {

        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final BiConsumer<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = makeConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setUrl("");

        // when
        configurator.accept(builder, config);

        // then
        verify(builder, never()).setPerformanceProfile(any());
    }

    @Test
    public void shouldAssignURL() throws Exception {

        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final BiConsumer<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = makeConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setUrl("http://void/");

        final ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

        // when
        configurator.accept(builder, config);

        // then
        verify(builder).setDataUpdateUrl(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getUrl());
    }

    @Test
    public void shouldIgnoreEmptyKey() throws Exception {

        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final BiConsumer<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = makeConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setLicenseKey("");

        // when
        configurator.accept(builder, config);

        // then
        verify(builder, never()).setDataUpdateLicenseKey(any());
    }

    @Test
    public void shouldAssignKey() throws Exception {

        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final BiConsumer<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = makeConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setLicenseKey("687-398475-34876-384678-34756-3487");

        final ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

        // when
        configurator.accept(builder, config);

        // then
        verify(builder).setDataUpdateLicenseKey(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getLicenseKey());
    }

    @Test
    public void shouldAssignAuto() throws Exception {

        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final BiConsumer<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = makeConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setAuto(true);

        final ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);

        // when
        configurator.accept(builder, config);

        // then
        verify(builder).setAutoUpdate(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getAuto());
    }

    @Test
    public void shouldAssignOnStartup() throws Exception {

        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final BiConsumer<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = makeConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setOnStartup(true);

        final ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);

        // when
        configurator.accept(builder, config);

        // then
        verify(builder).setDataUpdateOnStartup(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getOnStartup());
    }

    @Test
    public void shouldAssignWatchFileSystem() throws Exception {

        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final BiConsumer<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = makeConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setWatchFileSystem(true);

        final ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);

        // when
        configurator.accept(builder, config);

        // then
        verify(builder).setDataFileSystemWatcher(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getWatchFileSystem());
    }

    @Test
    public void shouldAssignPollingInterval() throws Exception {

        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);
        final BiConsumer<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> configurator
                = makeConfigurator();

        final DataFileUpdate config = new DataFileUpdate();
        config.setPollingInterval(643);

        final ArgumentCaptor<Integer> argumentCaptor = ArgumentCaptor.forClass(Integer.class);

        // when
        configurator.accept(builder, config);

        // then
        verify(builder).setUpdatePollingInterval(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(config.getPollingInterval());
    }
}
