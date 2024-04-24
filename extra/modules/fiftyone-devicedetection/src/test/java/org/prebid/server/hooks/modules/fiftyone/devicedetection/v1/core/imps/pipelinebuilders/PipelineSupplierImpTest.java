package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps.pipelinebuilders;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import fiftyone.pipeline.core.flowelements.Pipeline;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PipelineBuilderSpawner;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PipelineSupplier;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.model.config.DataFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PipelineSupplierImpTest {
    @Test
    public void shouldReturnBuiltPipeline() throws Exception {
        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);

        // when
        final Pipeline mockedPipeline = mock(Pipeline.class);
        when(builder.build()).thenReturn(mockedPipeline);

        final PipelineSupplier pipelineSupplier = new PipelineSupplierImp(builder);
        final Pipeline pipeline = pipelineSupplier.get();

        // then
        assertThat(pipeline).isEqualTo(mockedPipeline);
    }
}
