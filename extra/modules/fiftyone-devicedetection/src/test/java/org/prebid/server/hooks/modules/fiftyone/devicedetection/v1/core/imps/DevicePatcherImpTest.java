package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.iab.openrtb.request.Device;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatch;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DevicePatchPlan;

import java.util.AbstractMap;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

public class DevicePatcherImpTest {
    @Test
    public void shouldReturnOldDeviceIfPlanIsEmpty() {
        // given
        final Device oldDevice = Device.builder().build();
        final DevicePatchPlan patchPlan = new DevicePatchPlan(Collections.emptySet());

        // when
        final Device newDevice = new DevicePatcherImp().patchDevice(oldDevice, patchPlan, null);

        // then
        assertThat(newDevice).isEqualTo(oldDevice);
    }

    @Test
    public void shouldReturnOldDeviceIfPatchChangedNothing() {
        // given
        final Device oldDevice = Device.builder().build();
        final DevicePatchPlan patchPlan = simplePlan(((deviceBuilder, oldDevice1, newData) -> false));

        // when
        final Device newDevice = new DevicePatcherImp().patchDevice(oldDevice, patchPlan, null);

        // then
        assertThat(newDevice).isEqualTo(oldDevice);
    }

    @Test
    public void shouldPassDeviceDataToPatch() {
        // given
        final Device oldDevice = Device.builder().build();
        final DeviceInfo mockedData = mock(DeviceInfo.class);

        // when
        final boolean[] dataPassed = { false };
        final DevicePatchPlan patchPlan = simplePlan(((deviceBuilder, oldDevice1, newData) -> {
            assertThat(newData).isEqualTo(mockedData);
            dataPassed[0] = true;
            return false;
        }));
        final Device newDevice = new DevicePatcherImp().patchDevice(oldDevice, patchPlan, mockedData);

        // then
        assertThat(newDevice).isEqualTo(oldDevice);
        assertThat(dataPassed).containsExactly(true);
    }

    @Test
    public void shouldReturnNewDeviceWithPatchedData() {
        // given
        final Device oldDevice = Device.builder().build();
        final String newModel = "crafty";

        // when
        final DevicePatchPlan patchPlan = simplePlan(((deviceBuilder, oldDevice1, newData) -> {
            deviceBuilder.model(newModel);
            return true;
        }));
        final Device newDevice = new DevicePatcherImp().patchDevice(oldDevice, patchPlan, null);

        // then
        assertThat(newDevice).isNotEqualTo(oldDevice);
        assertThat(newDevice.getModel()).isEqualTo(newModel);
    }

    private static DevicePatchPlan simplePlan(DevicePatch patch) {
        return new DevicePatchPlan(Collections.singletonList(new AbstractMap.SimpleEntry<>("fakePatch", patch)));
    }
}
