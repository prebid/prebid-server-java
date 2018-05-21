package org.prebid.server.util;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;

/**
 * This class consists of {@code static} utility methods for operating HTTP requests.
 */
public final class HttpUtil {

    public static final CharSequence X_FORWARDED_FOR_HEADER = HttpHeaders.createOptimized("X-Forwarded-For");
    public static final CharSequence DNT_HEADER = HttpHeaders.createOptimized("DNT");
    public static final CharSequence X_REQUEST_AGENT_HEADER = HttpHeaders.createOptimized("X-Request-Agent");
    public static final String LJT_READER_COOKIE_NAME = "ljt_reader";

    private HttpUtil() {
    }

    /**
     * Detects whether browser is safari or not by user agent analysis.
     */
    public static boolean isSafari(String userAgent) {
        // this is a simple heuristic based on this article:
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Browser_detection_using_the_user_agent
        //
        // there are libraries available doing different kinds of User-Agent analysis but they impose performance
        // implications as well, example: https://github.com/nielsbasjes/yauaa
        return StringUtils.isNotBlank(userAgent)
                && userAgent.contains("AppleWebKit") && userAgent.contains("Safari")
                && !userAgent.contains("Chrome") && !userAgent.contains("Chromium");
    }

    /**
     * Checks the input string for using as URL.
     */
    public static String validateUrl(String url) {
        try {
            return new URL(url).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(String.format("URL supplied is not valid: %s", url), e);
        }
    }

    /**
     * Returns encoded URL for the given value.
     * <p>
     * The result can be safety used as the query string.
     */
    public static String encodeUrl(String format, Object... args) {
        final String uri = String.format(format, args);
        try {
            return URLEncoder.encode(uri, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(String.format("Cannot encode uri: %s", uri));
        }
    }

    /**
     * Returns decoded input value if supplied input not null, otherwise returns null.
     */
    public static String decodeUrl(String input) {
        if (StringUtils.isBlank(input)) {
            return null;
        }
        try {
            return URLDecoder.decode(input, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(String.format("Cannot decode input: %s", input));
        }
    }

    /**
     * Creates header from name and value, when value is not null or empty string.
     */
    public static void addHeaderIfValueIsNotEmpty(MultiMap headers, String headerName, String headerValue) {
        if (StringUtils.isNotEmpty(headerValue)) {
            headers.add(headerName, headerValue);
        }
    }
}
