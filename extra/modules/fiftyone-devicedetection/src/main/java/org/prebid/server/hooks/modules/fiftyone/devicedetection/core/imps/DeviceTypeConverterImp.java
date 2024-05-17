package org.prebid.server.hooks.modules.fiftyone.devicedetection.core.imps;

import org.prebid.server.hooks.modules.fiftyone.devicedetection.core.DeviceTypeConverter;

import java.util.Map;
import static java.util.Map.entry;

public final class DeviceTypeConverterImp implements DeviceTypeConverter {
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

    @Override
    public Integer apply(String deviceType) {
        return MAPPING.get(deviceType);
    }
}
