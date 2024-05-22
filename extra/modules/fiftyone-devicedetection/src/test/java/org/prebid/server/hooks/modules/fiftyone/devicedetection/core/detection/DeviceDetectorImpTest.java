package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.detection;

import com.iab.openrtb.request.Device;
import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.core.data.FlowData;
import fiftyone.pipeline.core.flowelements.Pipeline;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.adapters.DeviceInfoBuilderMethodSet;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.WritableDeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.EnrichmentResult;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.adapters.DeviceMirror;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeviceDetectorImpTest {
    private static DeviceDetector buildDeviceDetector(
            Supplier<Pipeline> pipelineSupplier,
            Function<CollectedEvidence, Map<String, String>> priorityEvidenceSelector,
            DeviceInfoPatcher deviceInfoPatcher)
    {
        return new DeviceRefinerImp(pipelineSupplier) {
            @Override
            public boolean populateDeviceInfo(
                    WritableDeviceInfo writableDeviceInfo,
                    CollectedEvidence collectedEvidence,
                    Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> devicePatchPlan,
                    EnrichmentResult.EnrichmentResultBuilder<?> enrichmentResultBuilder)
            {
                return super.populateDeviceInfo(
                        writableDeviceInfo,
                        collectedEvidence,
                        devicePatchPlan,
                        enrichmentResultBuilder);
            }

            @Override
            public Map<String, String> pickRelevantFrom(CollectedEvidence collectedEvidence) {
                return priorityEvidenceSelector.apply(collectedEvidence);
            }

            @Override
            public boolean patchDeviceInfo(
                    WritableDeviceInfo writableDeviceInfo,
                    Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan,
                    DeviceInfo newData,
                    EnrichmentResult.EnrichmentResultBuilder<?> enrichmentResultBuilder)
            {
                return deviceInfoPatcher.patchDeviceInfo(
                        writableDeviceInfo,
                        patchPlan,
                        newData,
                        enrichmentResultBuilder
                );
            }
        }::populateDeviceInfo;
    }

    private static DeviceInfo inferProperties(DeviceDetector deviceDetector) {
        final EnrichmentResult.EnrichmentResultBuilder<Device> resultBuilder = EnrichmentResult.builder();
        final DeviceInfoBuilderMethodSet<Device, Device.DeviceBuilder>.Adapter adapter
                = DeviceMirror.BUILDER_METHOD_SET.makeAdapter(Device.builder().build());
        if (deviceDetector.populateDeviceInfo(adapter, null, null, resultBuilder)) {
            return new DeviceMirror(adapter.rebuildBox());
        }
        final Exception processingException = resultBuilder.build().processingException();
        if (processingException != null) {
            throw new RuntimeException(processingException);
        }
        return null;
    }

    @Test
    public void shouldRethrowOnFailure() throws Exception {
        // given
        try(final Pipeline pipeline = mock(Pipeline.class);
            final FlowData flowData = mock(FlowData.class)) {
            final RuntimeException e = new RuntimeException();
            when(pipeline.createFlowData()).thenThrow(e);

            // given
            final DeviceDetector deviceDetector = buildDeviceDetector(
                    () -> pipeline,
                    null,
                    null);

            // when and then
            Assertions.assertThatThrownBy(() -> inferProperties(deviceDetector)).hasCause(e);
        }
    }

    @Test
    public void shouldGetDeviceDataFromPipeline() throws Exception {
        // given
        try(final Pipeline pipeline = mock(Pipeline.class)) {
            final FlowData flowData = mock(FlowData.class);
            when(pipeline.createFlowData()).thenReturn(flowData);

            final boolean[] getDeviceDataCalled = {false};
            when(flowData.get(DeviceData.class)).then(i -> {
                getDeviceDataCalled[0] = true;
                return null;
            });

            final DeviceDetector deviceDetector = buildDeviceDetector(
                    () -> pipeline,
                    evidence -> Collections.emptyMap(),
                    null
            );

            // when and then
            assertThat(inferProperties(deviceDetector)).isNull();
            assertThat(getDeviceDataCalled).containsExactly(true);
        }
    }

    @Test
    public void shouldReturnPatchedDevice() throws Exception {
        // given
        try(final Pipeline pipeline = mock(Pipeline.class)) {
            final FlowData flowData = mock(FlowData.class);
            when(pipeline.createFlowData()).thenReturn(flowData);
            when(flowData.get(DeviceData.class)).thenReturn(mock(DeviceData.class));
            final Device device = Device.builder()
                    .make("Pumpkin&Co")
                    .build();

            final DeviceDetector deviceDetector = buildDeviceDetector(
                    () -> pipeline,
                    evidence -> Collections.emptyMap(),
                    (writableDevice, patchPlan, newData, resultBuilder) -> {
                        writableDevice.setMake(device.getMake());
                        return true;
                    }
            );

            // when
            final DeviceInfo newDevice = inferProperties(deviceDetector);

            assertThat(newDevice).isNotNull();
            assertThat(newDevice.getMake()).isEqualTo(device.getMake());
        }
    }
}
