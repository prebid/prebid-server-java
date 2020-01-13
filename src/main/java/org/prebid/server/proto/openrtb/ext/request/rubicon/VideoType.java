package org.prebid.server.proto.openrtb.ext.request.rubicon;

public enum VideoType {
    REWARDED("rewarded");

    private final String name;

    VideoType(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
