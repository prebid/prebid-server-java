package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.adapters;

import fiftyone.devicedetection.shared.DeviceData;
import fiftyone.pipeline.engines.data.AspectPropertyValue;
import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.device.DeviceInfo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.Map.entry;

public class DeviceDataWrapper implements DeviceInfo {
    private final DeviceData deviceData;

    public DeviceDataWrapper(DeviceData deviceData) {
        this.deviceData = deviceData;
    }

    @Override
    public Integer getDeviceType() {
        final String rawDeviceType = getSafe(DeviceData::getDeviceType);
        if (rawDeviceType == null) {
            return null;
        }
        return convertDeviceType(rawDeviceType);
    }

    @Override
    public String getMake() {
        return getSafe(DeviceData::getHardwareVendor);
    }

    @Override
    public String getModel() {
        final String model = getSafe(DeviceData::getHardwareModel);
        if (!(model == null || model.isEmpty() || model.equals("Unknown"))) {
            return model;
        }
        final List<String> names = getSafe(DeviceData::getHardwareName);
        if (names == null || names.isEmpty()) {
            return null;
        }
        return String.join(",", names);
    }

    @Override
    public String getOs() {
        return getSafe(DeviceData::getPlatformName);
    }

    @Override
    public String getOsv() {
        return getSafe(DeviceData::getPlatformVersion);
    }

    @Override
    public Integer getH() {
        return getSafe(DeviceData::getScreenPixelsHeight);
    }

    @Override
    public Integer getW() {
        return getSafe(DeviceData::getScreenPixelsWidth);
    }

    @Override
    public Integer getPpi() {
        final Integer pixelsHeight = getSafe(DeviceData::getScreenPixelsHeight);
        if (pixelsHeight == null) {
            return null;
        }
        final Double inchesHeight = getSafe(DeviceData::getScreenInchesHeight);
        if (inchesHeight == null || inchesHeight == 0) {
            return null;
        }
        return (int)Math.round(pixelsHeight / inchesHeight);
    }

    @Override
    public BigDecimal getPixelRatio() {
        final Double rawRatio = getSafe(DeviceData::getPixelRatio);
        return (rawRatio != null) ? BigDecimal.valueOf(rawRatio) : null;
    }

    @Override
    public String getDeviceId() {
        return getSafe(DeviceData::getDeviceId);
    }

    private <T> T getSafe(Function<DeviceData, AspectPropertyValue<T>> propertyGetter) {
        try {
            final AspectPropertyValue<T> propertyValue = propertyGetter.apply(deviceData);
            if (propertyValue != null && propertyValue.hasValue()) {
                return propertyValue.getValue();
            }
        } catch (Exception e) {
            // nop -- not interested in errors on getting missing values.
        }
        return null;
    }

    // https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/main/AdCOM%20v1.0%20FINAL.md#list--device-types-
    private enum ORTBDeviceType {
        UNKNOWN,
        MOBILE_TABLET,
        PERSONAL_COMPUTER,
        CONNECTED_TV,
        PHONE,
        TABLET,
        CONNECTED_DEVICE,
        SET_TOP_BOX,
        OOH_DEVICE
    }
    private static final Map<String, Integer> MAPPING = Map.ofEntries(
            entry("Phone", ORTBDeviceType.PHONE.ordinal()),
            entry("Console", ORTBDeviceType.SET_TOP_BOX.ordinal()),
            entry("Desktop", ORTBDeviceType.PERSONAL_COMPUTER.ordinal()),
            entry("EReader", ORTBDeviceType.PERSONAL_COMPUTER.ordinal()),
            entry("IoT", ORTBDeviceType.CONNECTED_DEVICE.ordinal()),
            entry("Kiosk", ORTBDeviceType.OOH_DEVICE.ordinal()),
            entry("MediaHub", ORTBDeviceType.SET_TOP_BOX.ordinal()),
            entry("Mobile", ORTBDeviceType.MOBILE_TABLET.ordinal()),
            entry("Router", ORTBDeviceType.CONNECTED_DEVICE.ordinal()),
            entry("SmallScreen", ORTBDeviceType.CONNECTED_DEVICE.ordinal()),
            entry("SmartPhone", ORTBDeviceType.MOBILE_TABLET.ordinal()),
            entry("SmartSpeaker", ORTBDeviceType.CONNECTED_DEVICE.ordinal()),
            entry("SmartWatch", ORTBDeviceType.CONNECTED_DEVICE.ordinal()),
            entry("Tablet", ORTBDeviceType.TABLET.ordinal()),
            entry("Tv", ORTBDeviceType.CONNECTED_TV.ordinal()),
            entry("Vehicle Display", ORTBDeviceType.PERSONAL_COMPUTER.ordinal())
    );

    protected Integer convertDeviceType(String deviceType) {
        return MAPPING.get(deviceType);
    }
}
