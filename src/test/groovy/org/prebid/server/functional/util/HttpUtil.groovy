package org.prebid.server.functional.util

import org.apache.http.client.utils.URLEncodedUtils
import org.prebid.server.functional.model.UidsCookie

import static java.nio.charset.StandardCharsets.UTF_8

class HttpUtil implements ObjectMapperWrapper {

    public static final String UUID_REGEX = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/

    public static final String PG_TRX_ID_HEADER = "pg-trx-id"
    public static final String PG_IGNORE_PACING_HEADER = "X-Prebid-PG-ignore-pacing"
    public static final String AUTHORIZATION_HEADER = "Authorization"
    public static final String ACCEPT_HEADER = "Authorization"
    public static final String CONTENT_TYPE_HEADER = "Content-Type"
    public static final String COOKIE_HEADER = "cookie"
    public static final String CONTENT_ENCODING_HEADER = "Content-Encoding"

    public static final String CONTENT_TYPE_HEADER_VALUE = "application/json"
    public static final String CHARSET_HEADER_VALUE = "charset=utf-8"

    static String makeBasicAuthHeaderValue(String username, String password) {
        "Basic ${encodeWithBase64("$username:$password")}"
    }

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
