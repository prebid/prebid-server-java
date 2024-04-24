package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.imps;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Device.DeviceBuilder;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core.DeviceInfo;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;

import java.math.BigDecimal;

public final class DeviceMirror implements DeviceInfo {
    public static final String EXT_DEVICE_ID_KEY = "fiftyonedegrees_deviceId";

    private final Device device;

    public DeviceMirror(Device device) {
        this.device = device;
    }

    public Integer getDeviceType() {
        return device.getDevicetype();
    }
    public String getMake() {
        return device.getMake();
    }
    public String getModel() {
        return device.getModel();
    }
    public String getOs() {
        return device.getOs();
    }
    public String getOsv() {
        return device.getOsv();
    }
    public Integer getH() {
        return device.getH();
    }
    public Integer getW() {
        return device.getW();
    }
    public Integer getPpi() {
        return device.getPpi();
    }
    public BigDecimal getPixelRatio() {
        return device.getPxratio();
    }

    @Override
    public String getDeviceId() {
        final ExtDevice ext = device.getExt();
        if (ext == null) {
            return null;
        }
        final JsonNode savedValue = ext.getProperty(EXT_DEVICE_ID_KEY);
        return (savedValue != null && savedValue.isTextual()) ? savedValue.textValue() : null;
    }

    public static DeviceBuilder setDeviceId(DeviceBuilder deviceBuilder, Device device, String deviceId) {
        ExtDevice ext = null;
        if (device != null) {
            ext = device.getExt();
        }
        if (ext == null) {
            ext = ExtDevice.empty();
        }
        ext.addProperty(DeviceMirror.EXT_DEVICE_ID_KEY, new TextNode(deviceId));
        return deviceBuilder.ext(ext);
    }
}
