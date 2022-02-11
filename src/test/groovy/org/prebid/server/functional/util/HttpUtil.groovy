package org.prebid.server.functional.util

import org.prebid.server.functional.model.UidsCookie

class HttpUtil {

    public static final String UUID_REGEX = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/

    public static final String PG_TRX_ID_HEADER = "pg-trx-id"
    public static final String AUTHORIZATION_HEADER = "Authorization"
    public static final String ACCEPT_HEADER = "Authorization"
    public static final String CONTENT_TYPE_HEADER = "Content-Type"
    public static final String COOKIE_HEADER = "cookie"

    public static final String CONTENT_TYPE_HEADER_VALUE = "application/json"
    public static final String CHARSET_HEADER_VALUE = "charset=utf-8"

    static String makeBasicAuthHeaderValue(String username, String password) {
        "Basic ${encode("$username:$password")}"
    }

    static HashMap<String, String> getCookieHeader(ObjectMapperWrapper mapper, UidsCookie uidsCookie) {
        [(COOKIE_HEADER): makeUidsCookieHeaderValue(mapper.encode(uidsCookie))]
    }

    private static String makeUidsCookieHeaderValue(String uidsCookieJson) {
        "uids=${encode(uidsCookieJson)}"
    }

    private static encode(String string) {
        Base64.encoder.encodeToString(string.bytes)
    }
}
