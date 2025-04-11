package org.prebid.server.hooks.modules.com.scientiamobile.wurfl.devicedetection.resolver;

import com.iab.openrtb.request.BrandVersion;
import lombok.Getter;

public class PlatformNameVersion {

    @Getter
    private String platformName;

    private String platformVersion;

    public static PlatformNameVersion from(BrandVersion platform) {
        if (platform == null) {
            return null;
        }
        final PlatformNameVersion platformNameVersion = new PlatformNameVersion();
        platformNameVersion.platformName = platform.getBrand();
        platformNameVersion.platformVersion = HeadersResolver.versionFromTokens(platform.getVersion());
        return platformNameVersion;
    }

    public String getPlatformVersion() {
        return platformVersion;
    }

    public String toString() {
        return platformName + " " + platformVersion;
    }

}
