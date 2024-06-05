package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.hooks.rawAcutionRequest.detection;

import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Device;
import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.core.data.FlowData;
import fiftyone.pipeline.core.flowelements.Pipeline;
import fiftyone.pipeline.engines.data.AspectPropertyValue;
import fiftyone.pipeline.engines.exceptions.NoValueException;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.CollectedEvidence;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.EnrichmentResult;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;

import java.math.BigDecimal;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher.EXT_DEVICE_ID_KEY;
import static org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceEnricher.getDeviceId;

public class DevicePatchPlannerImpTest {
    private static <T> AspectPropertyValue<T> mockValue(T value) {
        return new AspectPropertyValue<>() {
            @Override
            public boolean hasValue() {
                return true;
            }

            @Override
            public T getValue() throws NoValueException {
                return value;
            }

            @Override
            public void setValue(T t) {
                throw new UnsupportedOperationException();
            }

            @Override
            public String getNoValueMessage() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void setNoValueMessage(String s) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private static EnrichmentResult patchDevice(
            Device device,
            DeviceData deviceData) throws Exception {
        final Pipeline pipeline = mock(Pipeline.class);
        final FlowData flowData = mock(FlowData.class);
        when(pipeline.createFlowData()).thenReturn(flowData);
        when(flowData.get(DeviceData.class)).thenReturn(deviceData);
        final DeviceEnricher deviceEnricher = new DeviceEnricher(pipeline);
        final CollectedEvidence collectedEvidence = CollectedEvidence.builder()
                .deviceUA("fake-UserAgent")
                .build();
        return deviceEnricher.populateDeviceInfo(device, collectedEvidence);
    }

    @Test
    public void shouldReturnAllPropertiesWhenDeviceIsEmpty() throws Exception {
        // given
        final Device device = Device.builder().build();

        // when
        final EnrichmentResult result
                = patchDevice(device, buildCompleteDeviceData());

        // then
        assertThat(result.enrichedFields()).hasSize(10);
    }

    @Test
    public void shouldReturnNullWhenDeviceIsFull() throws Exception {
        // given and when
        final EnrichmentResult result
                = patchDevice(buildCompleteDevice(), buildCompleteDeviceData());

        // then
        assertThat(result).isNull();
    }

    @Test
    public void shouldReturnDeviceTypePatchWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .devicetype(null)
                .build();

        // when
        final EnrichmentResult result
                = patchDevice(testDevice, buildCompleteDeviceData());

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getDevicetype()).isEqualTo(buildCompleteDevice().getDevicetype());
    }

    @Test
    public void shouldReturnMakePatchWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .make(null)
                .build();

        // when
        final EnrichmentResult result
                = patchDevice(testDevice, buildCompleteDeviceData());

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getMake()).isEqualTo(buildCompleteDevice().getMake());
    }

    @Test
    public void shouldReturnHWNameForModelIfHWModelIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .model(null)
                .build();
        final DeviceData deviceData = mock(DeviceData.class);
        final String expectedModel = "NinjaTech8888";
        when(deviceData.getHardwareName()).thenReturn(mockValue(Collections.singletonList(expectedModel)));
        when(deviceData.getHardwareModel()).thenThrow(new RuntimeException());

        // when
        final EnrichmentResult result
                = patchDevice(testDevice, deviceData);

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getModel()).isEqualTo(expectedModel);
    }

    @Test
    public void shouldReturnModelPatchWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .model(null)
                .build();

        // when
        final EnrichmentResult result
                = patchDevice(testDevice, buildCompleteDeviceData());

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getModel()).isEqualTo(buildCompleteDevice().getModel());
    }

    @Test
    public void shouldReturnOsPatchWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .os(null)
                .build();

        // when
        final EnrichmentResult result
                = patchDevice(testDevice, buildCompleteDeviceData());

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getOs()).isEqualTo(buildCompleteDevice().getOs());
    }

    @Test
    public void shouldReturnOsvPatchWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .osv(null)
                .build();

        // when
        final EnrichmentResult result
                = patchDevice(testDevice, buildCompleteDeviceData());

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getOsv()).isEqualTo(buildCompleteDevice().getOsv());
    }

    @Test
    public void shouldReturnHPatchWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .h(null)
                .build();

        // when
        final EnrichmentResult result
                = patchDevice(testDevice, buildCompleteDeviceData());

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getH()).isEqualTo(buildCompleteDevice().getH());
    }

    @Test
    public void shouldReturnWPatchWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .w(null)
                .build();

        // when
        final EnrichmentResult result
                = patchDevice(testDevice, buildCompleteDeviceData());

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getW()).isEqualTo(buildCompleteDevice().getW());
    }

    @Test
    public void shouldReturnPpiPatchWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .ppi(null)
                .build();

        // when
        final EnrichmentResult result
                = patchDevice(testDevice, buildCompleteDeviceData());

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getPpi()).isEqualTo(buildCompleteDevice().getPpi());
    }

    @Test
    public void shouldReturnPXRatioPatchWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .pxratio(null)
                .build();

        // when
        final EnrichmentResult result
                = patchDevice(testDevice, buildCompleteDeviceData());

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(result.enrichedDevice().getPxratio()).isEqualTo(buildCompleteDevice().getPxratio());
    }

    @Test
    public void shouldReturnDeviceIDPatchWhenItIsMissing() throws Exception {
        // given
        final Device testDevice = buildCompleteDevice().toBuilder()
                .ext(null)
                .build();

        // when
        final EnrichmentResult result
                = patchDevice(testDevice, buildCompleteDeviceData());

        // then
        assertThat(result.enrichedFields()).hasSize(1);
        assertThat(getDeviceId(result.enrichedDevice())).isEqualTo(getDeviceId(buildCompleteDevice()));
    }

    private static Device buildCompleteDevice() {
        final Device device = Device.builder()
                .devicetype(1)
                .make("StarFleet")
                .model("communicator")
                .os("NeutronAI")
                .osv("X-502")
                .h(5051)
                .w(3001)
                .ppi(1010)
                .pxratio(BigDecimal.valueOf(1.5))
                .ext(ExtDevice.empty())
                .build();
        device.getExt().addProperty(EXT_DEVICE_ID_KEY, new TextNode("fake-device-id"));
        return device;
    }

    private static DeviceData buildCompleteDeviceData() {
        final DeviceData deviceData = mock(DeviceData.class);
        when(deviceData.getDeviceType()).thenReturn(mockValue("Mobile"));
        when(deviceData.getHardwareVendor()).thenReturn(mockValue("StarFleet"));
        when(deviceData.getHardwareModel()).thenReturn(mockValue("communicator"));
        when(deviceData.getPlatformName()).thenReturn(mockValue("NeutronAI"));
        when(deviceData.getPlatformVersion()).thenReturn(mockValue("X-502"));
        when(deviceData.getScreenPixelsHeight()).thenReturn(mockValue(5051));
        when(deviceData.getScreenPixelsWidth()).thenReturn(mockValue(3001));
        when(deviceData.getScreenInchesHeight()).thenReturn(mockValue(5.0));
        when(deviceData.getPixelRatio()).thenReturn(mockValue(1.5));
        when(deviceData.getDeviceId()).thenReturn(mockValue("fake-device-id"));
        return deviceData;
    }
}
