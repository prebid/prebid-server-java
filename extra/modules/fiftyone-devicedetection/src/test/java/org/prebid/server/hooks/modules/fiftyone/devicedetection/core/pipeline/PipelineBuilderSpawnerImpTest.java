package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.pipeline;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFile;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.PerformanceConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PipelineBuilderSpawnerImpTest {
    private static PipelineBuilderSpawner<DeviceDetectionOnPremisePipelineBuilder> makeSpawner() throws Exception {
        return new PipelineProvider(null, null) {
            @Override
            protected DeviceDetectionOnPremisePipelineBuilder makeBuilder(
                    DataFile dataFile,
                    PerformanceConfig performanceConfig
            ) throws Exception {
                final DeviceDetectionOnPremisePipelineBuilder builder
                        = mock(DeviceDetectionOnPremisePipelineBuilder.class);
                when(builder.build()).thenReturn(null);
                return builder;
            }

            @Override
            public DeviceDetectionOnPremisePipelineBuilder makeRawBuilder(DataFile dataFile) throws Exception {
                return super.makeRawBuilder(dataFile);
            }
        }::makeRawBuilder;
    }
    
    @Test
    public void shouldReturnNonNull() throws Exception {
        // given
        final DataFile dataFile = new DataFile();
        dataFile.setPath("dummy.hash");
        final PipelineBuilderSpawner<DeviceDetectionOnPremisePipelineBuilder> builderSpawner
                = makeSpawner();

        // when
        final DeviceDetectionOnPremisePipelineBuilder pipelineBuilder = builderSpawner.makeBuilder(dataFile);

        // then
        assertThat(pipelineBuilder).isNotNull();
    }
    @Test
    public void shouldReturnNonNullWithCopy() throws Exception {
        // given
        final DataFile dataFile = new DataFile();
        dataFile.setPath("dummy.hash");
        dataFile.setMakeTempCopy(true);
        final PipelineBuilderSpawner<DeviceDetectionOnPremisePipelineBuilder> builderSpawner
                = makeSpawner();

        // when
        final DeviceDetectionOnPremisePipelineBuilder pipelineBuilder = builderSpawner.makeBuilder(dataFile);

        // then
        assertThat(pipelineBuilder).isNotNull();
    }
}
