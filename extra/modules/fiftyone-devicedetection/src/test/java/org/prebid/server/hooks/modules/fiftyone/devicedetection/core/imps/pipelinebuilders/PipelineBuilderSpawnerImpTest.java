package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.pipelinebuilders;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.PipelineBuilderSpawner;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class PipelineBuilderSpawnerImpTest {
    @Test
    public void shouldReturnNonNull() throws Exception {
        // given
        final DataFile dataFile = new DataFile();
        dataFile.setPath("dummy.hash");
        final PipelineBuilderSpawner<DeviceDetectionOnPremisePipelineBuilder> builderSpawner
                = new PipelineBuilderSpawnerImp();

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
                = new PipelineBuilderSpawnerImp();

        // when
        final DeviceDetectionOnPremisePipelineBuilder pipelineBuilder = builderSpawner.makeBuilder(dataFile);

        // then
        assertThat(pipelineBuilder).isNotNull();
    }
}
