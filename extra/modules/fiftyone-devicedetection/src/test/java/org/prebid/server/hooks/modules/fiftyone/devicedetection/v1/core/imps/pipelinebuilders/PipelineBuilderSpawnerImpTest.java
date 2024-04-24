package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.pipelinebuilders;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import org.junit.Before;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PipelineBuilderSpawner;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.config.DataFile;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
