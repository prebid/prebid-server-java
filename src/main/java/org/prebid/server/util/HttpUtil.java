package org.prebid.server.util;

import org.apache.commons.lang3.StringUtils;

/**
 * This class consists of {@code static} utility methods for operating HTTP requests
 */
public final class HttpUtil {

    /**
     * Detects whether browser is safari or not by user agent analysis
     */
    public static boolean isSafari(String userAgent) {
        // this is a simple heuristic based on this article:
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Browser_detection_using_the_user_agent
        //
        // there are libraries available doing different kinds of User-Agent analysis but they impose performance
        // implications as well, example: https://github.com/nielsbasjes/yauaa
        return StringUtils.isNotBlank(userAgent) && userAgent.contains("AppleWebKit") && userAgent.contains("Safari")
                && !userAgent.contains("Chrome") && !userAgent.contains("Chromium");
    }

    private HttpUtil() {

    }
}
