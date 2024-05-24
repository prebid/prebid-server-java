package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest.pipeline;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import fiftyone.pipeline.core.flowelements.Pipeline;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFile;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFileUpdate;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.PerformanceConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.FiftyOneDeviceDetectionRawAuctionRequestHook;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PipelineProviderTest {
    private static Supplier<Pipeline> makeProvider(
            DataFile dataFile,
            PerformanceConfig performanceConfig,
            PipelineBuilderSpawner<DeviceDetectionOnPremisePipelineBuilder> builderSpawner,
            BiConsumer<DeviceDetectionOnPremisePipelineBuilder, DataFileUpdate> updateOptionsMerger,
            BiConsumer<DeviceDetectionOnPremisePipelineBuilder, PerformanceConfig> performanceOptionsMerger
    ) throws Exception {
        final ModuleConfig moduleConfig = new ModuleConfig();
        moduleConfig.setDataFile(dataFile);
        moduleConfig.setPerformance(performanceConfig);
        return new FiftyOneDeviceDetectionRawAuctionRequestHook(moduleConfig) {
            @Override
            protected DeviceDetectionOnPremisePipelineBuilder makeBuilder() throws Exception {

                return super.makeBuilder();
            }

            @Override
            protected DeviceDetectionOnPremisePipelineBuilder makeRawBuilder(DataFile dataFile) throws Exception {

                return builderSpawner.makeBuilder(dataFile);
            }

            @Override
            protected void applyUpdateOptions(
                    DeviceDetectionOnPremisePipelineBuilder pipelineBuilder,
                    DataFileUpdate updateConfig) {

                updateOptionsMerger.accept(pipelineBuilder, updateConfig);
            }

            @Override
            protected void applyPerformanceOptions(
                    DeviceDetectionOnPremisePipelineBuilder pipelineBuilder,
                    PerformanceConfig performanceConfig) {

                performanceOptionsMerger.accept(pipelineBuilder, performanceConfig);
            }

            @Override
            public Pipeline getPipeline() {

                return super.getPipeline();
            }
        }
            ::getPipeline;
    }

    @Test
    public void shouldUseJoinedBuilder() throws Exception {

        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);

        final DataFile dataFile = new DataFile();
        dataFile.setUpdate(new DataFileUpdate());
        final PerformanceConfig performanceConfig = new PerformanceConfig();

        // when
        final Pipeline mockedPipeline = mock(Pipeline.class);
        when(builder.build()).thenReturn(mockedPipeline);

        final boolean[] builderSpawnerCalled = { false };
        final boolean[] updateAppliedCalled = { false };
        final boolean[] performanceAppliedCalled = { false };
        final Supplier<Pipeline> pipelineSupplier = makeProvider(
                dataFile,
                performanceConfig,
                dataFile1 -> {
                    builderSpawnerCalled[0] = true;
                    return builder;
                },
                (pipelineBuilder, dataFileUpdate) -> {
                    updateAppliedCalled[0] = true;
                    assertThat(pipelineBuilder).isEqualTo(builder);
                    assertThat(dataFileUpdate).isEqualTo(dataFile.getUpdate());
                },
                (pipelineBuilder, performanceConfig1) -> {
                    performanceAppliedCalled[0] = true;
                    assertThat(pipelineBuilder).isEqualTo(builder);
                    assertThat(performanceConfig1).isEqualTo(performanceConfig);
                }
        );
        final Pipeline pipeline = pipelineSupplier.get();

        // then
        assertThat(pipeline).isEqualTo(mockedPipeline);
        assertThat(builderSpawnerCalled).containsExactly(true);
        assertThat(updateAppliedCalled).containsExactly(true);
        assertThat(performanceAppliedCalled).containsExactly(true);
    }
}
