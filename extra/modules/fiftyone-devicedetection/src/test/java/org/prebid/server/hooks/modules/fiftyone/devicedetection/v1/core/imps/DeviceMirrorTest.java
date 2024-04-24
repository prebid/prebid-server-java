package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Device;
import org.junit.Test;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceInfo;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class DeviceMirrorTest {
    private static final Integer TEST_VALUE_INT = 29;
    private static final String TEST_VALUE_STRING = "dummy";

    @Test
    public void shouldReturnAllNullWhenDeviceIsEmpty() {
        // given
        final Device device = Device.builder().build();
        final DeviceInfo deviceMirror = new DeviceMirror(device);

        // when and then
        assertThat(deviceMirror.getDeviceType()).isNull();
        assertThat(deviceMirror.getMake()).isNull();
        assertThat(deviceMirror.getModel()).isNull();
        assertThat(deviceMirror.getOs()).isNull();
        assertThat(deviceMirror.getOsv()).isNull();
        assertThat(deviceMirror.getH()).isNull();
        assertThat(deviceMirror.getW()).isNull();
        assertThat(deviceMirror.getPpi()).isNull();
        assertThat(deviceMirror.getPixelRatio()).isNull();
        assertThat(deviceMirror.getDeviceId()).isNull();
    }


    @Test
    public void shouldReturnDeviceTypeWhenValueIsNotNull() {
        // given
        final Integer testValue = TEST_VALUE_INT;
        final Device device = Device.builder()
                .devicetype(testValue)
                .build();
        final DeviceInfo deviceMirror = new DeviceMirror(device);

        // when and then
        assertThat(deviceMirror.getDeviceType()).isEqualTo(testValue);
    }


    @Test
    public void shouldReturnMakeWhenValueIsNotNull() {
        // given
        final String testValue = TEST_VALUE_STRING;
        final Device device = Device.builder()
                .make(testValue)
                .build();
        final DeviceInfo deviceMirror = new DeviceMirror(device);

        // when and then
        assertThat(deviceMirror.getMake()).isEqualTo(testValue);
    }


    @Test
    public void shouldReturnModelWhenValueIsNotNull() {
        // given
        final String testValue = TEST_VALUE_STRING;
        final Device device = Device.builder()
                .model(testValue)
                .build();
        final DeviceInfo deviceMirror = new DeviceMirror(device);

        // when and then
        assertThat(deviceMirror.getModel()).isEqualTo(testValue);
    }


    @Test
    public void shouldReturnOsWhenValueIsNotNull() {
        // given
        final String testValue = TEST_VALUE_STRING;
        final Device device = Device.builder()
                .os(testValue)
                .build();
        final DeviceInfo deviceMirror = new DeviceMirror(device);

        // when and then
        assertThat(deviceMirror.getOs()).isEqualTo(testValue);
    }


    @Test
    public void shouldReturnOsvWhenValueIsNotNull() {
        // given
        final String testValue = TEST_VALUE_STRING;
        final Device device = Device.builder()
                .osv(testValue)
                .build();
        final DeviceInfo deviceMirror = new DeviceMirror(device);

        // when and then
        assertThat(deviceMirror.getOsv()).isEqualTo(testValue);
    }


    @Test
    public void shouldReturnHWhenValueIsNotNull() {
        // given
        final Integer testValue = TEST_VALUE_INT;
        final Device device = Device.builder()
                .h(testValue)
                .build();
        final DeviceInfo deviceMirror = new DeviceMirror(device);

        // when and then
        assertThat(deviceMirror.getH()).isEqualTo(testValue);
    }


    @Test
    public void shouldReturnWWhenValueIsNotNull() {
        // given
        final Integer testValue = TEST_VALUE_INT;
        final Device device = Device.builder()
                .w(testValue)
                .build();
        final DeviceInfo deviceMirror = new DeviceMirror(device);

        // when and then
        assertThat(deviceMirror.getW()).isEqualTo(testValue);
    }


    @Test
    public void shouldReturnPpiWhenValueIsNotNull() {
        // given
        final Integer testValue = TEST_VALUE_INT;
        final Device device = Device.builder()
                .ppi(testValue)
                .build();
        final DeviceInfo deviceMirror = new DeviceMirror(device);

        // when and then
        assertThat(deviceMirror.getPpi()).isEqualTo(testValue);
    }


    @Test
    public void shouldReturnPXRatioWhenValueIsNotNull() {
        // given
        final BigDecimal testValue = BigDecimal.valueOf(TEST_VALUE_INT);
        final Device device = Device.builder()
                .pxratio(testValue)
                .build();
        final DeviceInfo deviceMirror = new DeviceMirror(device);

        // when and then
        assertThat(deviceMirror.getPixelRatio()).isEqualTo(testValue);
    }

    @Test
    public void shouldReturnDeviceIdWhenValueIsNotNull() {
        // given
        final String testValue = TEST_VALUE_STRING;
        final Device device = DeviceMirror.setDeviceId(Device.builder(), null, testValue).build();
        final DeviceInfo deviceMirror = new DeviceMirror(device);

        // when and then
        assertThat(deviceMirror.getDeviceId()).isEqualTo(testValue);
    }

    @Test
    public void shouldPreserveOldDataInExtOnSetDeviceId() {
        // given
        final String savedKey = "alpha";
        final String savedValue = "kappa";
        final String deviceId = "lambda";

        final Device existingDevice = Device.builder()
                .ext(ExtDevice.empty()).build();
        existingDevice.getExt().addProperty(savedKey, new TextNode(savedValue));

        // when
        final Device newDevice = DeviceMirror.setDeviceId(Device.builder(), existingDevice, deviceId).build();
        final DeviceInfo deviceMirror = new DeviceMirror(newDevice);

        // then
        assertThat(deviceMirror.getDeviceId()).isEqualTo(deviceId);
        final JsonNode savedNode = newDevice.getExt().getProperty(savedKey);
        assertThat(savedNode).isNotNull();
        assertThat(savedNode.isTextual()).isTrue();
        assertThat(savedNode.textValue()).isEqualTo(savedValue);
    }
}
