package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest.detection;

import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.core.data.FlowData;
import fiftyone.pipeline.core.flowelements.Pipeline;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.EnrichmentResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeviceEnrichmentTest {
    @Test
    public void shouldReportErrorOnPipelineException() throws Exception {
        // given
        final Pipeline pipeline = mock(Pipeline.class);
        final Exception e = new RuntimeException();
        when(pipeline.createFlowData()).thenThrow(e);

        // when
        final DeviceEnricher deviceEnricher = new DeviceEnricher(pipeline);
        final EnrichmentResult result = deviceEnricher.populateDeviceInfo(null, null);

        // then
        assertThat(result.processingException()).isEqualTo(e);
    }

    @Test
    public void shouldReportErrorOnProcessException() throws Exception {
        // given
        final Pipeline pipeline = mock(Pipeline.class);
        final FlowData flowData = mock(FlowData.class);
        final Exception e = new RuntimeException();
        when(pipeline.createFlowData()).thenReturn(flowData);
        doThrow(e).when(flowData).process();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder().build();

        // when
        final DeviceEnricher deviceEnricher = new DeviceEnricher(pipeline);
        final EnrichmentResult result = deviceEnricher.populateDeviceInfo(
                null,
                collectedEvidence);

        // then
        assertThat(result.processingException()).isEqualTo(e);
    }

    @Test
    public void shouldReturnNullOnNullDeviceData() throws Exception {
        // given
        final Pipeline pipeline = mock(Pipeline.class);
        final FlowData flowData = mock(FlowData.class);
        final Exception e = new RuntimeException();
        when(pipeline.createFlowData()).thenReturn(flowData);
        when(flowData.get(DeviceData.class)).thenReturn(null);
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder().build();

        // when
        final DeviceEnricher deviceEnricher = new DeviceEnricher(pipeline);
        final EnrichmentResult result = deviceEnricher.populateDeviceInfo(
                null,
                collectedEvidence);

        // then
        assertThat(result).isNull();
        verify(flowData, times(1)).get(DeviceData.class);
    }
}
