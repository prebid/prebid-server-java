package org.prebid.server.hooks.modules.fiftyone.devicedetection.v1.core;

import java.util.Map;
import java.util.Optional;

// https://github.com/InteractiveAdvertisingBureau/AdCOM/blob/main/AdCOM%20v1.0%20FINAL.md#list--device-types-
public enum OrtbDeviceType {
    UNKNOWN,
    MOBILE_TABLET,
    PERSONAL_COMPUTER,
    CONNECTED_TV,
    PHONE,
    TABLET,
    CONNECTED_DEVICE,
    SET_TOP_BOX,
    OOH_DEVICE;

    private static final Map<String, OrtbDeviceType> DEVICE_FIELD_MAPPING = Map.ofEntries(
            Map.entry("Phone", OrtbDeviceType.PHONE),
            Map.entry("Console", OrtbDeviceType.SET_TOP_BOX),
            Map.entry("Desktop", OrtbDeviceType.PERSONAL_COMPUTER),
            Map.entry("EReader", OrtbDeviceType.PERSONAL_COMPUTER),
            Map.entry("IoT", OrtbDeviceType.CONNECTED_DEVICE),
            Map.entry("Kiosk", OrtbDeviceType.OOH_DEVICE),
            Map.entry("MediaHub", OrtbDeviceType.SET_TOP_BOX),
            Map.entry("Mobile", OrtbDeviceType.MOBILE_TABLET),
            Map.entry("Router", OrtbDeviceType.CONNECTED_DEVICE),
            Map.entry("SmallScreen", OrtbDeviceType.CONNECTED_DEVICE),
            Map.entry("SmartPhone", OrtbDeviceType.MOBILE_TABLET),
            Map.entry("SmartSpeaker", OrtbDeviceType.CONNECTED_DEVICE),
            Map.entry("SmartWatch", OrtbDeviceType.CONNECTED_DEVICE),
            Map.entry("Tablet", OrtbDeviceType.TABLET),
            Map.entry("Tv", OrtbDeviceType.CONNECTED_TV),
            Map.entry("Vehicle Display", OrtbDeviceType.PERSONAL_COMPUTER));

    public static OrtbDeviceType resolveFrom(String deviceType) {
        return Optional.ofNullable(DEVICE_FIELD_MAPPING.get(deviceType)).orElse(UNKNOWN);
    }
}
