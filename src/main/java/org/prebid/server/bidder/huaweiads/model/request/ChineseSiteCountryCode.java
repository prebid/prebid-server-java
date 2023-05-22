package org.prebid.server.bidder.huaweiads.model.request;

import java.util.Arrays;

public enum ChineseSiteCountryCode {

    CN;

    public static boolean isContainsByName(String name) {
        return Arrays.stream(ChineseSiteCountryCode.values())
                .map(Enum::name)
                .anyMatch(codeName -> codeName.equalsIgnoreCase(name));
    }
}
