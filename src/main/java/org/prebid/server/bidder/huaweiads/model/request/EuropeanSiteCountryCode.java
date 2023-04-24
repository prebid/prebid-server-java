package org.prebid.server.bidder.huaweiads.model.request;

import java.util.Arrays;

public enum EuropeanSiteCountryCode {

    AX, AL, AD, AU, AT, BE, BA, BG, CA, HR, CY, CZ,
    DK, EE, FO, FI, FR, DE, GI, GR, GL, GG, VA, HU,
    IS, IE, IM, IL, IT, JE, YK, LV, LI, LT, LU, MT,
    MD, MC, ME, NL, AN, NZ, NO, PL, PT, RO, MF, VC,
    SM, RS, SX, SK, SI, ES, SE, CH, TR, UA, GB, US,
    MK, SJ, BQ, PM, CW;

    public static boolean isContainsByName(String name) {
        return Arrays.stream(EuropeanSiteCountryCode.values())
                .anyMatch(code -> code.name().equalsIgnoreCase(name));
    }
}
