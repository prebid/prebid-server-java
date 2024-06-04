package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest.pipeline;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFile;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PipelineBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class PipelineBuilderSpawnerImpTest {
    private static PipelineBuilderSpawner<DeviceDetectionOnPremisePipelineBuilder> makeSpawner() throws Exception {

        return new PipelineBuilder() {
            @Override
            public DeviceDetectionOnPremisePipelineBuilder makeRawBuilder(DataFile dataFile) throws Exception {

                return super.makeRawBuilder(dataFile);
            }
        }
            ::makeRawBuilder;
    }

    @Test
    public void shouldReturnNonNull() throws Exception {

        // given
        final DataFile dataFile = new DataFile();
        dataFile.setPath("dummy.hash");
        final PipelineBuilderSpawner<DeviceDetectionOnPremisePipelineBuilder> builderSpawner
                = makeSpawner();

        // when
        final DeviceDetectionOnPremisePipelineBuilder pipelineBuilder = builderSpawner.spawn(dataFile);

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
        final DeviceDetectionOnPremisePipelineBuilder pipelineBuilder = builderSpawner.spawn(dataFile);

        // then
        assertThat(pipelineBuilder).isNotNull();
    }
}
