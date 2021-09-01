package org.prebid.server.deals.model;

public enum DeviceType {

    MOBILE("mobile"),
    DESKTOP("desktop"),
    TV("connected tv"),
    PHONE("phone"),
    DEVICE("connected device"),
    SET_TOP_BOX("set top box"),
    TABLET("tablet");

    private String name;

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

        switch (deviceType) {
            case "Mobile Phone":
            case "Mobile":
            case "SmartPhone":
            case "SmallScreen":
                return MOBILE;
            case "Desktop":
            case "Single-board Computer":
                return DESKTOP;
            case "TV":
            case "Tv":
                return TV;
            case "Fixed Wireless Phone":
            case "Vehicle Phone":
                return PHONE;
            case "Tablet":
                return TABLET;
            case "Digital Home Assistant":
            case "Digital Signage Media Player":
            case "eReader":
            case "EReader":
            case "Console":
            case "Games Console":
            case "Media Player":
            case "Payment Terminal":
            case "Refrigerator":
            case "Vehicle Multimedia System":
            case "Weighing Scale":
            case "Wristwatch":
            case "SmartWatch":
                return DEVICE;
            case "Set Top Box":
                // might not be correct for 51degrees (https://51degrees.com/resources/property-dictionary)
            case "MediaHub":
                return SET_TOP_BOX;
            default:
                return null;
        }
    }
}
