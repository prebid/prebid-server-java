package org.prebid.server.functional.util

import org.apache.http.client.utils.URLEncodedUtils
import org.prebid.server.functional.model.UidsCookie

import static java.nio.charset.StandardCharsets.UTF_8

class HttpUtil implements ObjectMapperWrapper {


    public static final String ACCEPT_HEADER = "Authorization"
    public static final String CONTENT_TYPE_HEADER = "Content-Type"
    public static final String COOKIE_HEADER = "cookie"
    public static final String CONTENT_ENCODING_HEADER = "Content-Encoding"
    public static final String REFERER_HEADER = "Referer"
    public static final String SEC_BROWSING_TOPICS_HEADER = "Sec-Browsing-Topics"
    public static final String SET_COOKIE_HEADER = 'Set-Cookie'
    public static final String COOKIE_DEPRECATION_HEADER = 'Sec-Cookie-Deprecation'

    static HashMap<String, String> getCookieHeader(UidsCookie uidsCookie) {
        [(COOKIE_HEADER): makeUidsCookieHeaderValue(encode(uidsCookie))]
    }

    static HashMap<String, String> getCookieHeader(String value1, String value2) {
        [(COOKIE_HEADER): "$value1=$value2"]
    }

    private static String decodeUrl(String url) {
        URLDecoder.decode(url, UTF_8)
    }

    private static String makeUidsCookieHeaderValue(String uidsCookieJson) {
        "uids=${encodeWithBase64(uidsCookieJson)}"
    }

    private static String encodeWithBase64(String string) {
        Base64.encoder.encodeToString(string.bytes)
    }

    static String findUrlParameterValue(String url, String parameter) {
        URLEncodedUtils.parse(decodeUrl(url), UTF_8).find { it.name == parameter }.value
    }
}
