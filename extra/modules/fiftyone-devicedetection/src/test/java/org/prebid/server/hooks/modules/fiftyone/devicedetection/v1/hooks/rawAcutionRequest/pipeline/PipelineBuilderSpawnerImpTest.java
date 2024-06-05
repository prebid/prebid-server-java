package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest.pipeline;

import fiftyone.pipeline.core.flowelements.PipelineBuilderBase;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFile;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.DataFileUpdate;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.PerformanceConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PipelineBuilder;

import static org.assertj.core.api.Assertions.assertThat;

public class PipelineBuilderSpawnerImpTest {
    private static PipelineBuilderBase<?> spawnBuilder(DataFile dataFile) throws Exception {
        final ModuleConfig moduleConfig = new ModuleConfig();
        moduleConfig.setDataFile(dataFile);
        dataFile.setUpdate(new DataFileUpdate());
        moduleConfig.setPerformance(new PerformanceConfig());
        return new PipelineBuilder(null).build(moduleConfig);
    }

    @Test
    public void shouldReturnNonNull() throws Exception {
        // given
        final DataFile dataFile = new DataFile();
        dataFile.setPath("dummy.hash");

        // when
        final PipelineBuilderBase<?> pipelineBuilder = spawnBuilder(dataFile);

        // then
        assertThat(pipelineBuilder).isNotNull();
    }

    @Test
    public void shouldReturnNonNullWithCopy() throws Exception {
        // given
        final DataFile dataFile = new DataFile();
        dataFile.setPath("dummy.hash");
        dataFile.setMakeTempCopy(true);

        // when
        final PipelineBuilderBase<?> pipelineBuilder = spawnBuilder(dataFile);

        // then
        assertThat(pipelineBuilder).isNotNull();
    }
}
