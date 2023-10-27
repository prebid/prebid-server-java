package org.prebid.server.bidder.silverpush;

import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

public class SilverPushDeviceTypeResolver {

    private SilverPushDeviceTypeResolver() {
    }

    private static final Pattern MOBILE_PATTERN =
            Pattern.compile("(ios|ipod|ipad|iphone|android)", Pattern.CASE_INSENSITIVE);
    private static final Pattern CTV_PATTERN =
            Pattern.compile("(smart[-]?tv|hbbtv|appletv|googletv|hdmi|netcast\\.tv|viera|"
                            + "nettv|roku|\\bdtv\\b|sonydtv|inettvbrowser|\\btv\\b)",
                    Pattern.CASE_INSENSITIVE);

    public static String resolveOs(String userAgent) {
        if (StringUtils.containsIgnoreCase(userAgent, "Windows")) {
            return "Windows";
        } else if (StringUtils.containsAnyIgnoreCase(userAgent, "iPhone", "iPod", "iPad")) {
            return "iOS";
        } else if (StringUtils.containsIgnoreCase(userAgent, "Mac OS X")) {
            return "macOS";
        } else if (StringUtils.containsIgnoreCase(userAgent, "Android")) {
            return "Android";
        } else if (StringUtils.containsIgnoreCase(userAgent, "Linux")) {
            return "Linux";
        }

        return "Unknown";
    }

    public static Integer resolveDeviceType(String userAgent) {
        if (isMobile(userAgent)) {
            return 1;
        }
        if (isCTV(userAgent)) {
            return 3;
        }

        return 2;
    }

    private static boolean isMobile(String userAgent) {
        return MOBILE_PATTERN.matcher(userAgent).find();
    }

    private static boolean isCTV(String userAgent) {
        return CTV_PATTERN.matcher(userAgent).find();
    }
}
