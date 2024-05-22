package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.detection;

import com.iab.openrtb.request.Device;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.adapters.DeviceInfoBuilderMethodSet;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.WritableDeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary.EnrichmentResult;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.adapters.DeviceMirror;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.function.BiPredicate;

import static org.assertj.core.api.Assertions.assertThat;

public class DevicePatchPlannerImpTest {
    private final static int PROPERTIES_COUNT = 10;

    private final static Device COMPLETE_DEVICE = DeviceMirror.setDeviceId(
            Device.builder()
                    .devicetype(1)
                    .make("StarFleet")
                    .model("communicator")
                    .os("NeutronAI")
                    .osv("X-502")
                    .h(5001)
                    .w(3001)
                    .ppi(803)
                    .pxratio(BigDecimal.valueOf(1.5)),
            null,
            "fake-device-id"
    ).build();

    private static Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> buildPatchPlanFor(Device device) {
        return new DeviceRefinerImp(null) {
            @Override
            public Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> buildPatchPlanFor(DeviceInfo deviceInfo) {
                return super.buildPatchPlanFor(deviceInfo);
            }
        }.buildPatchPlanFor(new DeviceMirror(device));
    }
    
    private static Device patchDeviceInfo(Device rawDevice, Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan, DeviceInfo newData) {
        final EnrichmentResult.EnrichmentResultBuilder<Device> resultBuilder = EnrichmentResult.builder();
        final DeviceInfoBuilderMethodSet<Device, ?>.Adapter adapter
                = DeviceMirror.BUILDER_METHOD_SET.makeAdapter(rawDevice);
        if (new DeviceRefinerImp(null) {
            @Override
            public boolean patchDeviceInfo(
                    WritableDeviceInfo writableDeviceInfo,
                    Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan,
                    DeviceInfo newData,
                    EnrichmentResult.EnrichmentResultBuilder<?> enrichmentResultBuilder)
            {
                return super.patchDeviceInfo(
                        writableDeviceInfo,
                        patchPlan,
                        newData,
                        enrichmentResultBuilder
                );
            }}
                .patchDeviceInfo(
                    adapter,
                    patchPlan,
                    newData,
                    resultBuilder)
        ) {
            return adapter.rebuildBox();
        }

        return null;
    }

    @Test
    public void shouldReturnAllPropertiesWhenDeviceIsEmpty() {
        // given
        final Device device = Device.builder().build();

        // when
        final Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan = buildPatchPlanFor(device);

        // then
        assertThat(patchPlan.size()).isEqualTo(PROPERTIES_COUNT);
    }

    @Test
    public void shouldReturnZeroPropertiesWhenDeviceIsFull() {
        // given and when
        final Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan = buildPatchPlanFor(COMPLETE_DEVICE);

        // then
        assertThat(patchPlan).isEmpty();
    }

    @Test
    public void shouldReturnDeviceTypePatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .devicetype(null)
                .build();

        // when
        final Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan = buildPatchPlanFor(testDevice);
        final Device newDevice = patchDeviceInfo(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.size()).isEqualTo(1);
        assertThat(newDevice.getDevicetype()).isEqualTo(COMPLETE_DEVICE.getDevicetype());
    }


    @Test
    public void shouldReturnMakePatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .make(null)
                .build();

        // when
        final Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan = buildPatchPlanFor(testDevice);
        final Device newDevice = patchDeviceInfo(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.size()).isEqualTo(1);
        assertThat(newDevice.getMake()).isEqualTo(COMPLETE_DEVICE.getMake());
    }


    @Test
    public void shouldReturnModelPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .model(null)
                .build();

        // when
        final Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan = buildPatchPlanFor(testDevice);
        final Device newDevice = patchDeviceInfo(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.size()).isEqualTo(1);
        assertThat(newDevice.getModel()).isEqualTo(COMPLETE_DEVICE.getModel());
    }


    @Test
    public void shouldReturnOsPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .os(null)
                .build();

        // when
        final Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan = buildPatchPlanFor(testDevice);
        final Device newDevice = patchDeviceInfo(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.size()).isEqualTo(1);
        assertThat(newDevice.getOs()).isEqualTo(COMPLETE_DEVICE.getOs());
    }


    @Test
    public void shouldReturnOsvPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .osv(null)
                .build();

        // when
        final Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan = buildPatchPlanFor(testDevice);
        final Device newDevice = patchDeviceInfo(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.size()).isEqualTo(1);
        assertThat(newDevice.getOsv()).isEqualTo(COMPLETE_DEVICE.getOsv());
    }


    @Test
    public void shouldReturnHPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .h(null)
                .build();

        // when
        final Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan = buildPatchPlanFor(testDevice);
        final Device newDevice = patchDeviceInfo(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.size()).isEqualTo(1);
        assertThat(newDevice.getH()).isEqualTo(COMPLETE_DEVICE.getH());
    }


    @Test
    public void shouldReturnWPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .w(null)
                .build();

        // when
        final Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan = buildPatchPlanFor(testDevice);
        final Device newDevice = patchDeviceInfo(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.size()).isEqualTo(1);
        assertThat(newDevice.getW()).isEqualTo(COMPLETE_DEVICE.getW());
    }


    @Test
    public void shouldReturnPpiPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .ppi(null)
                .build();

        // when
        final Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan = buildPatchPlanFor(testDevice);
        final Device newDevice = patchDeviceInfo(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.size()).isEqualTo(1);
        assertThat(newDevice.getPpi()).isEqualTo(COMPLETE_DEVICE.getPpi());
    }


    @Test
    public void shouldReturnPXRatioPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .pxratio(null)
                .build();

        // when
        final Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan = buildPatchPlanFor(testDevice);
        final Device newDevice = patchDeviceInfo(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.size()).isEqualTo(1);
        assertThat(newDevice.getPxratio()).isEqualTo(COMPLETE_DEVICE.getPxratio());
    }

    @Test
    public void shouldReturnDeviceIDPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .ext(null)
                .build();

        // when
        final Collection<Map.Entry<String, BiPredicate<WritableDeviceInfo, DeviceInfo>>> patchPlan = buildPatchPlanFor(testDevice);
        final Device newDevice = patchDeviceInfo(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.size()).isEqualTo(1);
        assertThat(new DeviceMirror(newDevice).getDeviceId())
                .isEqualTo(new DeviceMirror(COMPLETE_DEVICE).getDeviceId());
    }
}
