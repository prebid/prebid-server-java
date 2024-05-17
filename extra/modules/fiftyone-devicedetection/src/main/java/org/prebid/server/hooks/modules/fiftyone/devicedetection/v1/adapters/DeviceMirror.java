package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Device.DeviceBuilder;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceInfo;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps.DeviceInfoBuilderMethodSet;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;

import java.math.BigDecimal;

public final class DeviceMirror implements DeviceInfo {
    public static final String EXT_DEVICE_ID_KEY = "fiftyonedegrees_deviceId";

    private final Device device;

    public DeviceMirror(Device device) {
        this.device = device;
    }

    /** @see com.iab.openrtb.request.Device#getDevicetype() */
    public Integer getDeviceType() {
        return device.getDevicetype();
    }
    /** @see com.iab.openrtb.request.Device#getMake() */
    public String getMake() {
        return device.getMake();
    }
    /** @see com.iab.openrtb.request.Device#getModel() */
    public String getModel() {
        return device.getModel();
    }
    /** @see com.iab.openrtb.request.Device#getOs() */
    public String getOs() {
        return device.getOs();
    }
    /** @see com.iab.openrtb.request.Device#getOsv() */
    public String getOsv() {
        return device.getOsv();
    }
    /** @see com.iab.openrtb.request.Device#getH() */
    public Integer getH() {
        return device.getH();
    }
    /** @see com.iab.openrtb.request.Device#getW() */
    public Integer getW() {
        return device.getW();
    }
    /** @see com.iab.openrtb.request.Device#getPpi() */
    public Integer getPpi() {
        return device.getPpi();
    }
    /** @see com.iab.openrtb.request.Device#getPxratio() */
    public BigDecimal getPixelRatio() {
        return device.getPxratio();
    }

    /**
     * Consists of four components separated by a hyphen symbol:
     * Hardware-Platform-Browser-IsCrawler where
     * each Component represents an ID of the corresponding Profile.
     *
     * @see fiftyone.devicedetection.hash.engine.onpremise.data.DeviceDataHash#getDeviceId()
     */
    @Override
    public String getDeviceId() {
        final ExtDevice ext = device.getExt();
        if (ext == null) {
            return null;
        }
        final JsonNode savedValue = ext.getProperty(EXT_DEVICE_ID_KEY);
        return (savedValue != null && savedValue.isTextual()) ? savedValue.textValue() : null;
    }

    /**
     * Consists of four components separated by a hyphen symbol:
     * Hardware-Platform-Browser-IsCrawler where
     * each Component represents an ID of the corresponding Profile.
     *
     * @see fiftyone.devicedetection.hash.engine.onpremise.data.DeviceDataHash#getDeviceId()
     *
     * @param deviceBuilder Writable builder to save device ID into.
     * @param device Raw (non-builder) form of device before modification.
     * @param deviceId New Device ID value.
     * @return {@code deviceBuilder} with an updated value for Device ID.
     */
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

    public static final DeviceInfoBuilderMethodSet<Device, Device.DeviceBuilder> BUILDER_METHOD_SET =
            new DeviceInfoBuilderMethodSet<>(
                    Device::toBuilder,
                    Device.DeviceBuilder::build,
                    DeviceBuilder::devicetype,
                    DeviceBuilder::make,
                    DeviceBuilder::model,
                    DeviceBuilder::os,
                    DeviceBuilder::osv,
                    DeviceBuilder::h,
                    DeviceBuilder::w,
                    DeviceBuilder::ppi,
                    DeviceBuilder::pxratio,
                    device -> (deviceBuilder, value) -> setDeviceId(deviceBuilder, device, value)
            );
}
