package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest.detection;

import com.iab.openrtb.request.Device;
import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.core.data.FlowData;
import fiftyone.pipeline.core.flowelements.Pipeline;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.EnrichmentResult;

import java.util.Collections;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class DeviceEnrichmentTest {

    private static BiFunction<Device,
            CollectedEvidence,
            EnrichmentResult> buildHook(
                    Pipeline pipeline,
                    BiFunction<
                            Device,
                            DeviceData,
                            EnrichmentResult> patcher,
                    Function<CollectedEvidence, Map<String, String>> evidenceCollector) throws Exception {

        return new DeviceEnricher(pipeline) {
            @Override
            protected EnrichmentResult patchDevice(Device device, DeviceData deviceData) {

                return patcher.apply(device, deviceData);
            }

            @Override
            protected Map<String, String> pickRelevantFrom(CollectedEvidence collectedEvidence) {

                return evidenceCollector.apply(collectedEvidence);
            }

            @Override
            public EnrichmentResult populateDeviceInfo(
                    Device device,
                    CollectedEvidence collectedEvidence) {

                return super.populateDeviceInfo(device, collectedEvidence);
            }
        }
            ::populateDeviceInfo;
    }

    @Test
    public void shouldReportErrorOnPipelineException() throws Exception {

        // given
        final Pipeline pipeline = mock(Pipeline.class);
        final Exception e = new RuntimeException();
        when(pipeline.createFlowData()).thenThrow(e);

        // when
        final BiFunction<
                Device,
                CollectedEvidence,
                EnrichmentResult> hook
                = buildHook(pipeline, null, null);
        final EnrichmentResult result = hook.apply(null, null);

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
        final Map<String, String> evidence = Collections.singletonMap("k", "v");
        final ArgumentCaptor<Map<String, String>> evidenceCaptor = ArgumentCaptor.forClass(Map.class);

        // when
        final boolean[] collectorCalled = { false };
        final BiFunction<
                Device,
                CollectedEvidence,
                EnrichmentResult> hook
                = buildHook(pipeline, null, ev -> {
                    collectorCalled[0] = true;
                    assertThat(ev).isEqualTo(collectedEvidence);
                    return evidence;
                });
        final EnrichmentResult result = hook.apply(
                null,
                collectedEvidence);

        // then
        assertThat(result.processingException()).isEqualTo(e);
        assertThat(collectorCalled).containsExactly(true);
        verify(flowData).addEvidence(evidenceCaptor.capture());
        assertThat(evidenceCaptor.getAllValues()).containsExactly(evidence);
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
        final Map<String, String> evidence = Collections.singletonMap("x", "z");
        final ArgumentCaptor<Map<String, String>> evidenceCaptor = ArgumentCaptor.forClass(Map.class);

        // when
        final boolean[] collectorCalled = { false };
        final BiFunction<
                Device,
                CollectedEvidence,
                EnrichmentResult> hook
                = buildHook(
                        pipeline,
                        null,
                        ev -> {
                            collectorCalled[0] = true;
                            assertThat(ev).isEqualTo(collectedEvidence);
                            return evidence;
                        });
        final EnrichmentResult result = hook.apply(
                null,
                collectedEvidence);

        // then
        assertThat(result).isNull();
        assertThat(collectorCalled).containsExactly(true);
        verify(flowData).addEvidence(evidenceCaptor.capture());
        assertThat(evidenceCaptor.getAllValues()).containsExactly(evidence);
    }

    @Test
    public void shouldReturnPatchResult() throws Exception {

        // given
        final Pipeline pipeline = mock(Pipeline.class);
        final FlowData flowData = mock(FlowData.class);
        when(pipeline.createFlowData()).thenReturn(flowData);
        final Device device = Device.builder().build();
        final DeviceData deviceData = mock(DeviceData.class);
        when(flowData.get(DeviceData.class)).thenReturn(deviceData);
        final Device mergedDevice = Device.builder().build();
        final EnrichmentResult preparedResult
                = EnrichmentResult.builder()
                .enrichedDevice(mergedDevice)
                .build();
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder().build();
        final Map<String, String> evidence = Collections.singletonMap("q", "w");
        final ArgumentCaptor<Map<String, String>> evidenceCaptor = ArgumentCaptor.forClass(Map.class);

        // when
        final boolean[] patcherCalled = { false };
        final boolean[] collectorCalled = { false };
        final BiFunction<
                Device,
                CollectedEvidence,
                EnrichmentResult> hook
                = buildHook(pipeline,
                    (dev, devData) -> {
                        patcherCalled[0] = true;
                        assertThat(dev).isEqualTo(device);
                        assertThat(devData).isEqualTo(deviceData);
                        return preparedResult;
                    },
                    ev -> {
                        collectorCalled[0] = true;
                        assertThat(ev).isEqualTo(collectedEvidence);
                        return evidence;
                    });
        final EnrichmentResult result = hook.apply(
                device,
                CollectedEvidence.builder().build());

        // then
        assertThat(patcherCalled).containsExactly(true);
        assertThat(result).isEqualTo(preparedResult);
        assertThat(collectorCalled).containsExactly(true);
        verify(flowData).addEvidence(evidenceCaptor.capture());
        assertThat(evidenceCaptor.getAllValues()).containsExactly(evidence);
    }
}
