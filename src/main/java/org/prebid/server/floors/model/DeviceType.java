package org.prebid.server.floors.model;

public enum DeviceType {

    DESKTOP, PHONE, TABLET;

    public String toLowerCaseString() {
        return name().toLowerCase();
    }
}
