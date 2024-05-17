package org.prebid.server.hooks.modules.fiftyone.devicedetection.model.boundary;

import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DevicePatchPlan;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.DeviceInfoPatcherImp;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.DevicePatchPlannerImp;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.// then
        assertThat;

public class DeviceInfoCloneTest {
    @Test
    public void shouldReturnDeviceType() {
        // given
        final Integer deviceType = 16;
        // when
        final DeviceInfo deviceInfo = DeviceInfoClone.builder()
                .deviceType(deviceType)
                .build();
        // then
        assertThat(deviceInfo.getDeviceType()).isEqualTo(deviceType);
    }
    @Test
    public void shouldReturnMake() {
        // given
        final String make = "fake-make";
        // when
        final DeviceInfo deviceInfo = DeviceInfoClone.builder()
                .make(make)
                .build();
        // then
        assertThat(deviceInfo.getMake()).isEqualTo(make);
    }
    @Test
    public void shouldReturnModel() {
        // given
        final String model = "fake-model";
        // when
        final DeviceInfo deviceInfo = DeviceInfoClone.builder()
                .model(model)
                .build();
        // then
        assertThat(deviceInfo.getModel()).isEqualTo(model);
    }
    @Test
    public void shouldReturnOs() {
        // given
        final String os = "fake-os";
        // when
        final DeviceInfo deviceInfo = DeviceInfoClone.builder()
                .os(os)
                .build();
        // then
        assertThat(deviceInfo.getOs()).isEqualTo(os);
    }
    @Test
    public void shouldReturnOsv() {
        // given
        final String osv = "fake-osV";
        // when
        final DeviceInfo deviceInfo = DeviceInfoClone.builder()
                .osv(osv)
                .build();
        // then
        assertThat(deviceInfo.getOsv()).isEqualTo(osv);
    }
    @Test
    public void shouldReturnH() {
        // given
        final Integer h = 320;
        // when
        final DeviceInfo deviceInfo = DeviceInfoClone.builder()
                .h(h)
                .build();
        // then
        assertThat(deviceInfo.getH()).isEqualTo(h);
    }
    @Test
    public void shouldReturnW() {
        // given
        final Integer w = 240;
        // when
        final DeviceInfo deviceInfo = DeviceInfoClone.builder()
                .w(w)
                .build();
        // then
        assertThat(deviceInfo.getW()).isEqualTo(w);
    }
    @Test
    public void shouldReturnPpi() {
        // given
        final Integer ppi = 60;
        // when
        final DeviceInfo deviceInfo = DeviceInfoClone.builder()
                .ppi(ppi)
                .build();
        // then
        assertThat(deviceInfo.getPpi()).isEqualTo(ppi);
    }
    @Test
    public void shouldReturnPixelRatio() {
        // given
        final BigDecimal pixelRatio = BigDecimal.TWO;
        // when
        final DeviceInfo deviceInfo = DeviceInfoClone.builder()
                .pixelRatio(pixelRatio)
                .build();
        // then
        assertThat(deviceInfo.getPixelRatio()).isEqualTo(pixelRatio);
    }
    @Test
    public void shouldReturnDeviceId() {
        // given
        final String deviceId = "97696958";
        // when
        final DeviceInfo deviceInfo = DeviceInfoClone.builder()
                .deviceId(deviceId)
                .build();
        // then
        assertThat(deviceInfo.getDeviceId()).isEqualTo(deviceId);
    }
    @Test
    public void shouldSetDeviceIdViaPatcher() {
        // given
        final DeviceInfoPatcherImp<DeviceInfoClone, DeviceInfoClone.DeviceInfoCloneBuilder> patcher
                = new DeviceInfoPatcherImp<>(DeviceInfoClone.BUILDER_METHOD_SET::makeAdapter);
        final DeviceInfoClone baseClone = DeviceInfoClone.builder()
                .w(640)
                .h(480)
                .build();
        final DeviceInfoClone patchData = DeviceInfoClone.builder().deviceId("krabazumba9000").build();
        // when
        final DevicePatchPlan patchPlan = new DevicePatchPlannerImp().buildPatchPlanFor(baseClone);
        final DeviceInfo patchedClone = patcher.patchDeviceInfo(baseClone, patchPlan, patchData);
        // then
        assertThat(patchedClone.getH()).isEqualTo(baseClone.getH());
        assertThat(patchedClone.getW()).isEqualTo(baseClone.getW());
        assertThat(patchedClone.getDeviceId()).isEqualTo(patchData.getDeviceId());
    }

    @Test
    public void shouldHaveDescription() {
        // given
        final DeviceInfoClone theClone = DeviceInfoClone.builder().deviceId("doomazanga3k").build();
        // when and then
        assertThat(theClone.toString()).isNotBlank();
    }
}
