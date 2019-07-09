package org.prebid.server.bidder.sharethrough;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

class HttpUserAgentUtil {

    private static final Pattern IOS_DEVICE_PATTERN = Pattern.compile("iPhone|iPad|iPod", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANDROID_PATTERN = Pattern.compile("Android", Pattern.CASE_INSENSITIVE);

    private static final Pattern CHROME_PATTERN = Pattern.compile("Chrome/(\\d+)");
    private static final Pattern CHROME_IOS_PATTERN = Pattern.compile("CriOS/(\\d+)");
    private static final Pattern SAFARI_PATTERN = Pattern.compile("Version/(\\d+)");

    private HttpUserAgentUtil() {
    }

    static boolean isAndroid(String ua) {
        return ANDROID_PATTERN.matcher(ua).find();
    }

    static boolean isIos(String ua) {
        return IOS_DEVICE_PATTERN.matcher(ua).find();
    }

    static boolean isAtMinChromeVersion(String ua, int minVersion) {
        return compareVersion(ua, CHROME_PATTERN, minVersion);
    }

    static boolean isAtMinChromeIosVersion(String ua, int minVersion) {
        return compareVersion(ua, CHROME_IOS_PATTERN, minVersion);
    }

    static boolean isAtMinSafariVersion(String ua, int minVersion) {
        return compareVersion(ua, SAFARI_PATTERN, minVersion);
    }

    private static boolean compareVersion(String ua, Pattern uaPattern, Integer minVersion) {
        try {
            return getUaVersion(ua, uaPattern) >= minVersion;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int getUaVersion(String ua, Pattern uaPattern) {
        Matcher matcher = uaPattern.matcher(ua);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        } else {
            throw new IllegalArgumentException("Cant resolve string " + ua);
        }
    }
}

