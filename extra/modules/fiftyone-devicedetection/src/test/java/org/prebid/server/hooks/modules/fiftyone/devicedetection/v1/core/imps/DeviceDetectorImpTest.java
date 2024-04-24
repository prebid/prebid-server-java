package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.iab.openrtb.request.Device;
import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.core.data.FlowData;
import fiftyone.pipeline.core.flowelements.Pipeline;
import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceDetector;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceInfo;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DeviceDetectorImpTest {
    @Test
    public void shouldRethrowOnFailure() throws Exception {
        // given
        try(final Pipeline pipeline = mock(Pipeline.class)) {
            final FlowData flowData = mock(FlowData.class);
            final RuntimeException e = new RuntimeException();
            when(pipeline.createFlowData()).thenThrow(e);

            // given
            final DeviceDetector deviceDetector = new DeviceDetectorImp(
                    () -> pipeline,
                    null,
                    null,
                    null);

            // when and then
            Assertions.assertThatThrownBy(() -> deviceDetector.inferProperties(null, null)).hasCause(e);
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

            final DeviceDetector deviceDetector = new DeviceDetectorImp(
                    () -> pipeline,
                    evidence -> Collections.emptyMap(),
                    deviceType -> null,
                    null
            );

            // when and then
            assertThat(deviceDetector.inferProperties(null, null)).isNull();
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

            final DeviceDetector deviceDetector = new DeviceDetectorImp(
                    () -> pipeline,
                    evidence -> Collections.emptyMap(),
                    deviceType -> null,
                    (rawDevice, patchPlan, newData) -> device
            );

            // when
            final DeviceInfo newDevice = deviceDetector.inferProperties(null, null);

            assertThat(newDevice).isNotNull();
            assertThat(newDevice.getMake()).isEqualTo(device.getMake());
        }
    }
}
