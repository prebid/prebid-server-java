package org.prebid.server.deals.model;

public enum DeviceType {

    MOBILE("mobile"),
    DESKTOP("desktop"),
    TV("connected tv"),
    PHONE("phone"),
    DEVICE("connected device"),
    SET_TOP_BOX("set top box"),
    TABLET("tablet");

    private final String name;

    DeviceType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static DeviceType resolveDeviceType(String deviceType) {
        if (deviceType == null) {
            return null;
        }

        return switch (deviceType) {
            case "Mobile Phone", "Mobile", "SmartPhone", "SmallScreen" -> MOBILE;
            case "Desktop", "Single-board Computer" -> DESKTOP;
            case "TV", "Tv" -> TV;
            case "Fixed Wireless Phone", "Vehicle Phone" -> PHONE;
            case "Tablet" -> TABLET;
            case "Digital Home Assistant", "Digital Signage Media Player",
                    "eReader", "EReader", "Console", "Games Console", "Media Player",
                    "Payment Terminal", "Refrigerator", "Vehicle Multimedia System",
                    "Weighing Scale", "Wristwatch", "SmartWatch" -> DEVICE;
            // might not be correct for 51degrees (https://51degrees.com/resources/property-dictionary)
            case "Set Top Box", "MediaHub" -> SET_TOP_BOX;
            default -> null;
        };
    }
}
