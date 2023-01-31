package org.prebid.server.bidder;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

public class UsersyncUtil {

    public static final String CALLBACK_URL_TEMPLATE = """
            %s/setuid?\
            bidder=%s\
            &gdpr={{gdpr}}\
            &gdpr_consent={{gdpr_consent}}\
            &us_privacy={{us_privacy}}\
            &gpp={{gpp}}\
            &gpp_sid={{gpp_sid}}\
            &uid=%s""";

    public static final String FORMAT_PARAMETER = "f";

    private UsersyncUtil() {
    }

    public static UsersyncFormat resolveFormat(UsersyncMethod method) {
        return ObjectUtils.firstNonNull(method.getFormatOverride(), method.getType().format);
    }

    public static String enrichUrlWithFormat(String url, UsersyncFormat format) {
        final String filteredUrl = StringUtils.stripToEmpty(url);
        if (StringUtils.isEmpty(filteredUrl)) {
            return url;
        }

        return hasTwoOrMoreParameters(url)
                ? insertFormatParameter(url, format.name)
                : appendFormatParameter(url, format.name);
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
