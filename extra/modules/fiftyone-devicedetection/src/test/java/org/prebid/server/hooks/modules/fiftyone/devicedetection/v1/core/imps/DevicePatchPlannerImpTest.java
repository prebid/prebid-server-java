package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.iab.openrtb.request.Device;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatchPlan;

import java.math.BigDecimal;

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

    @Test
    public void shouldReturnAllPropertiesWhenDeviceIsEmpty() {
        // given
        final Device device = Device.builder().build();

        // when
        final DevicePatchPlan patchPlan = new DevicePatchPlannerImp().buildPatchPlanFor(device);

        // then
        assertThat(patchPlan.patches().size()).isEqualTo(PROPERTIES_COUNT);
    }

    @Test
    public void shouldReturnZeroPropertiesWhenDeviceIsFull() {
        // given and when
        final DevicePatchPlan patchPlan = new DevicePatchPlannerImp().buildPatchPlanFor(COMPLETE_DEVICE);

        // then
        assertThat(patchPlan.patches()).isEmpty();
        assertThat(patchPlan.isEmpty()).isTrue();
    }

    @Test
    public void shouldReturnDeviceTypePatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .devicetype(null)
                .build();

        // when
        final DevicePatchPlan patchPlan = new DevicePatchPlannerImp().buildPatchPlanFor(testDevice);
        final Device newDevice = new DevicePatcherImp().patchDevice(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.patches().size()).isEqualTo(1);
        assertThat(newDevice.getDevicetype()).isEqualTo(COMPLETE_DEVICE.getDevicetype());
    }


    @Test
    public void shouldReturnMakePatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .make(null)
                .build();

        // when
        final DevicePatchPlan patchPlan = new DevicePatchPlannerImp().buildPatchPlanFor(testDevice);
        final Device newDevice = new DevicePatcherImp().patchDevice(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.patches().size()).isEqualTo(1);
        assertThat(newDevice.getMake()).isEqualTo(COMPLETE_DEVICE.getMake());
    }


    @Test
    public void shouldReturnModelPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .model(null)
                .build();

        // when
        final DevicePatchPlan patchPlan = new DevicePatchPlannerImp().buildPatchPlanFor(testDevice);
        final Device newDevice = new DevicePatcherImp().patchDevice(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.patches().size()).isEqualTo(1);
        assertThat(newDevice.getModel()).isEqualTo(COMPLETE_DEVICE.getModel());
    }


    @Test
    public void shouldReturnOsPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .os(null)
                .build();

        // when
        final DevicePatchPlan patchPlan = new DevicePatchPlannerImp().buildPatchPlanFor(testDevice);
        final Device newDevice = new DevicePatcherImp().patchDevice(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.patches().size()).isEqualTo(1);
        assertThat(newDevice.getOs()).isEqualTo(COMPLETE_DEVICE.getOs());
    }


    @Test
    public void shouldReturnOsvPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .osv(null)
                .build();

        // when
        final DevicePatchPlan patchPlan = new DevicePatchPlannerImp().buildPatchPlanFor(testDevice);
        final Device newDevice = new DevicePatcherImp().patchDevice(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.patches().size()).isEqualTo(1);
        assertThat(newDevice.getOsv()).isEqualTo(COMPLETE_DEVICE.getOsv());
    }


    @Test
    public void shouldReturnHPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .h(null)
                .build();

        // when
        final DevicePatchPlan patchPlan = new DevicePatchPlannerImp().buildPatchPlanFor(testDevice);
        final Device newDevice = new DevicePatcherImp().patchDevice(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.patches().size()).isEqualTo(1);
        assertThat(newDevice.getH()).isEqualTo(COMPLETE_DEVICE.getH());
    }


    @Test
    public void shouldReturnWPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .w(null)
                .build();

        // when
        final DevicePatchPlan patchPlan = new DevicePatchPlannerImp().buildPatchPlanFor(testDevice);
        final Device newDevice = new DevicePatcherImp().patchDevice(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.patches().size()).isEqualTo(1);
        assertThat(newDevice.getW()).isEqualTo(COMPLETE_DEVICE.getW());
    }


    @Test
    public void shouldReturnPpiPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .ppi(null)
                .build();

        // when
        final DevicePatchPlan patchPlan = new DevicePatchPlannerImp().buildPatchPlanFor(testDevice);
        final Device newDevice = new DevicePatcherImp().patchDevice(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.patches().size()).isEqualTo(1);
        assertThat(newDevice.getPpi()).isEqualTo(COMPLETE_DEVICE.getPpi());
    }


    @Test
    public void shouldReturnPXRatioPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .pxratio(null)
                .build();

        // when
        final DevicePatchPlan patchPlan = new DevicePatchPlannerImp().buildPatchPlanFor(testDevice);
        final Device newDevice = new DevicePatcherImp().patchDevice(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.patches().size()).isEqualTo(1);
        assertThat(newDevice.getPxratio()).isEqualTo(COMPLETE_DEVICE.getPxratio());
    }

    @Test
    public void shouldReturnDeviceIDPatchWhenItIsMissing() {
        // given
        final Device testDevice = COMPLETE_DEVICE.toBuilder()
                .ext(null)
                .build();

        // when
        final DevicePatchPlan patchPlan = new DevicePatchPlannerImp().buildPatchPlanFor(testDevice);
        final Device newDevice = new DevicePatcherImp().patchDevice(
                testDevice,
                patchPlan,
                new DeviceMirror(COMPLETE_DEVICE)
        );

        // then
        assertThat(patchPlan.patches().size()).isEqualTo(1);
        assertThat(new DeviceMirror(newDevice).getDeviceId())
                .isEqualTo(new DeviceMirror(COMPLETE_DEVICE).getDeviceId());
    }
}
