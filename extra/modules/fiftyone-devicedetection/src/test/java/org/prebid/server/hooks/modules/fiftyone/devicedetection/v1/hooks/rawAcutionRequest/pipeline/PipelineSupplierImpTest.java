package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest.pipeline;

import fiftyone.devicedetection.DeviceDetectionOnPremisePipelineBuilder;
import fiftyone.pipeline.core.flowelements.Pipeline;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.config.ModuleConfig;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.PipelineBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PipelineSupplierImpTest {
    private static PipelineSupplier makeSupplier(DeviceDetectionOnPremisePipelineBuilder builder) throws Exception {
        return () -> new PipelineBuilder() {
            @Override
            protected DeviceDetectionOnPremisePipelineBuilder makeBuilder(ModuleConfig moduleConfig) throws Exception {

                return builder;
            }
        }.build(null);
    }

    @Test
    public void shouldReturnBuiltPipeline() throws Exception {

        // given
        final DeviceDetectionOnPremisePipelineBuilder builder = mock(DeviceDetectionOnPremisePipelineBuilder.class);

        // when
        final Pipeline mockedPipeline = mock(Pipeline.class);
        when(builder.build()).thenReturn(mockedPipeline);

        final PipelineSupplier pipelineSupplier = makeSupplier(builder);
        final Pipeline pipeline = pipelineSupplier.get();

        // then
        assertThat(pipeline).isEqualTo(mockedPipeline);
    }
}
