package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import fiftyone.devicedetection.DeviceDetectionPipelineBuilder;
import fiftyone.pipeline.core.flowelements.Pipeline;
import fiftyone.pipeline.engines.Constants;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFile;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFileUpdate;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.PerformanceConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PipelineBuilderTest {
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private ModuleConfig moduleConfig;
    private DataFileUpdate dataFileUpdate;
    private PerformanceConfig performanceConfig;

    @Mock
    private DeviceDetectionPipelineBuilder builderPrime;
    @Mock
    private DeviceDetectionOnPremisePipelineBuilder builder;
    @Mock
    private Pipeline pipeline;

    @Before
    public void setUp() throws Exception {
        dataFileUpdate = new DataFileUpdate();
        performanceConfig = new PerformanceConfig();
        moduleConfig = new ModuleConfig();
        moduleConfig.setDataFile(new DataFile());
        moduleConfig.getDataFile().setUpdate(dataFileUpdate);
        moduleConfig.setPerformance(performanceConfig);
        when(builderPrime.useOnPremise(any(), anyBoolean())).thenReturn(builder);
        when(builder.build()).thenReturn(pipeline);
    }

    // MARK: - applyUpdateOptions

    @Test
    public void buildShouldIgnoreEmptyUrl() throws Exception {
        // given
        dataFileUpdate.setUrl("");

        // when
        new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        verify(builder, never()).setPerformanceProfile(any());
    }

    @Test
    public void buildShouldAssignURL() throws Exception {
        // given
        dataFileUpdate.setUrl("http://void/");

        final ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

        // when
        new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        verify(builder).setDataUpdateUrl(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(dataFileUpdate.getUrl());
    }

    @Test
    public void buildShouldIgnoreEmptyLicenseKey() throws Exception {
        // given
        dataFileUpdate.setLicenseKey("");

        // when
        new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        verify(builder, never()).setDataUpdateLicenseKey(any());
    }

    @Test
    public void buildShouldAssignKey() throws Exception {
        // given
        dataFileUpdate.setLicenseKey("687-398475-34876-384678-34756-3487");

        final ArgumentCaptor<String> argumentCaptor = ArgumentCaptor.forClass(String.class);

        // when
        new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        verify(builder).setDataUpdateLicenseKey(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(dataFileUpdate.getLicenseKey());
    }

    @Test
    public void buildShouldAssignAuto() throws Exception {
        // given
        dataFileUpdate.setAuto(true);

        final ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);

        // when
        new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        verify(builder).setAutoUpdate(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(dataFileUpdate.getAuto());
    }

    @Test
    public void buildShouldAssignOnStartup() throws Exception {
        // given
        dataFileUpdate.setOnStartup(true);

        final ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);

        // when
        new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        verify(builder).setDataUpdateOnStartup(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(dataFileUpdate.getOnStartup());
    }

    @Test
    public void buildShouldAssignWatchFileSystem() throws Exception {
        // given
        dataFileUpdate.setWatchFileSystem(true);

        final ArgumentCaptor<Boolean> argumentCaptor = ArgumentCaptor.forClass(Boolean.class);

        // when
        new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        verify(builder).setDataFileSystemWatcher(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(dataFileUpdate.getWatchFileSystem());
    }

    @Test
    public void buildShouldAssignPollingInterval() throws Exception {
        // given
        dataFileUpdate.setPollingInterval(643);

        final ArgumentCaptor<Integer> argumentCaptor = ArgumentCaptor.forClass(Integer.class);

        // when
        new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        verify(builder).setUpdatePollingInterval(argumentCaptor.capture());
        assertThat(argumentCaptor.getAllValues()).containsExactly(dataFileUpdate.getPollingInterval());
    }

    // MARK: - applyPerformanceOptions

    @Test(expected = IllegalArgumentException.class)
    public void buildShouldThrowWhenProfileIsUnknown() throws Exception {
        // given
        performanceConfig.setProfile("ghost");

        try {
            // when
            new PipelineBuilder(moduleConfig).build(builderPrime);
        } finally {
            // then
            verify(builder, never()).setPerformanceProfile(any());
        }
    }

    @Test
    public void buildShouldIgnoreEmptyProfile() throws Exception {
        // given
        performanceConfig.setProfile("");

        // when
        new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        verify(builder, never()).setPerformanceProfile(any());
    }

    @Test
    public void buildShouldAssignMaxPerformance() throws Exception {
        // given
        performanceConfig.setProfile("mAxperFORMance");

        final ArgumentCaptor<Constants.PerformanceProfiles> profilesArgumentCaptor
                = ArgumentCaptor.forClass(Constants.PerformanceProfiles.class);

        // when
        new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        verify(builder).setPerformanceProfile(profilesArgumentCaptor.capture());
        assertThat(profilesArgumentCaptor.getAllValues()).containsExactly(Constants.PerformanceProfiles.MaxPerformance);
    }

    @Test
    public void buildShouldAssignConcurrency() throws Exception {
        // given
        performanceConfig.setConcurrency(398476);

        final ArgumentCaptor<Integer> concurrenciesArgumentCaptor = ArgumentCaptor.forClass(Integer.class);

        // when
        new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        verify(builder).setConcurrency(concurrenciesArgumentCaptor.capture());
        assertThat(concurrenciesArgumentCaptor.getAllValues()).containsExactly(performanceConfig.getConcurrency());
    }

    @Test
    public void buildShouldAssignDifference() throws Exception {
        // given
        performanceConfig.setDifference(498756);

        final ArgumentCaptor<Integer> profilesArgumentCaptor = ArgumentCaptor.forClass(Integer.class);

        // when
        new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        verify(builder).setDifference(profilesArgumentCaptor.capture());
        assertThat(profilesArgumentCaptor.getAllValues()).containsExactly(performanceConfig.getDifference());
    }

    @Test
    public void buildShouldAssignAllowUnmatched() throws Exception {
        // given
        performanceConfig.setAllowUnmatched(true);

        final ArgumentCaptor<Boolean> allowUnmatchedArgumentCaptor = ArgumentCaptor.forClass(Boolean.class);

        // when
        new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        verify(builder).setAllowUnmatched(allowUnmatchedArgumentCaptor.capture());
        assertThat(allowUnmatchedArgumentCaptor.getAllValues()).containsExactly(performanceConfig.getAllowUnmatched());
    }

    @Test
    public void buildShouldAssignDrift() throws Exception {
        // given
        performanceConfig.setDrift(1348);

        final ArgumentCaptor<Integer> driftsArgumentCaptor = ArgumentCaptor.forClass(Integer.class);

        // when
        new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        verify(builder).setDrift(driftsArgumentCaptor.capture());
        assertThat(driftsArgumentCaptor.getAllValues()).containsExactly(performanceConfig.getDrift());
    }

    // MARK: - build

    @Test
    public void buildShouldReturnNonNull() throws Exception {
        // given
        moduleConfig.getDataFile().setPath("dummy.hash");

        // when
        final Pipeline returnedPipeline = new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        assertThat(returnedPipeline).isEqualTo(pipeline);
    }

    @Test
    public void buildShouldReturnNonNullWithCopy() throws Exception {
        // given
        moduleConfig.getDataFile().setPath("dummy.hash");
        moduleConfig.getDataFile().setMakeTempCopy(true);

        // when
        final Pipeline returnedPipeline = new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        assertThat(returnedPipeline).isEqualTo(pipeline);
    }

    @Test
    public void buildShouldNotThrowWhenMinimal() throws Exception {
        // given
        moduleConfig.getDataFile().setPath("dummy.hash");
        moduleConfig.getDataFile().setUpdate(null);
        moduleConfig.setPerformance(null);

        // when
        final Pipeline returnedPipeline = new PipelineBuilder(moduleConfig).build(builderPrime);

        // then
        assertThat(returnedPipeline).isEqualTo(pipeline);
    }
}
