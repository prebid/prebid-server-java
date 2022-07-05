package org.prebid.server.bidder;

import org.apache.commons.lang3.StringUtils;

public class UsersyncUtil {

    public static final String FORMAT_PARAMETER = "f";

    private UsersyncUtil() {
    }

    /**
     * Places format query string param into the given url.
     * <p>
     * Caution: it doesn't care if it already exists in url, just adds new one.
     * <p>
     * Note: format is inserted before the last param for safety reason because of
     * usersync url can be appended with UID on the exchange side without parsing query string.
     */
    public static String enrichUsersyncUrlWithFormat(String url, UsersyncMethodType type) {
        if (StringUtils.isEmpty(url)) {
            return url;
        }

        return hasTwoOrMoreParameters(url)
                ? insertFormatParameter(url, type.format)
                : appendFormatParameter(url, type.format);
    }

    private static boolean hasTwoOrMoreParameters(String url) {
        return url.indexOf('&') != -1;
    }

    private static String insertFormatParameter(String url, String formatValue) {
        final int lastParamIndex = url.lastIndexOf('&');

        return url.substring(0, lastParamIndex)
                + "&" + FORMAT_PARAMETER + "=" + formatValue
                + url.substring(lastParamIndex);
    }

    private static String appendFormatParameter(String url, String formatValue) {
        final String separator = url.indexOf('?') != -1 ? "&" : "?";
        return url + separator + FORMAT_PARAMETER + "=" + formatValue;
    }
}
