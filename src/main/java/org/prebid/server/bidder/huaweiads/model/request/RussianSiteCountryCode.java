package org.prebid.server.bidder.huaweiads.model.request;

import java.util.Arrays;

public enum RussianSiteCountryCode {

    RU;

    public static boolean isContainsByName(String name) {
        return Arrays.stream(RussianSiteCountryCode.values())
                .map(Enum::name)
                .anyMatch(codeName -> codeName.equalsIgnoreCase(name));
    }
}
