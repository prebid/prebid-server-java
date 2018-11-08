package org.prebid.server.util;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
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

    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    public static final CharSequence X_FORWARDED_FOR_HEADER = HttpHeaders.createOptimized("X-Forwarded-For");
    public static final CharSequence DNT_HEADER = HttpHeaders.createOptimized("DNT");
    public static final CharSequence X_REQUEST_AGENT_HEADER = HttpHeaders.createOptimized("X-Request-Agent");

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
     * Creates general headers for request.
     */
    public static MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .add(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON);
    }

    /**
     * Creates header from name and value, when value is not null or empty string.
     */
    public static void addHeaderIfValueIsNotEmpty(MultiMap headers, String headerName, String headerValue) {
        if (StringUtils.isNotEmpty(headerValue)) {
            headers.add(headerName, headerValue);
        }
    }

    /**
     * Determines IP-Address by checking "X-Forwarded-For", "X-Real-IP" http headers or remote host address
     * if both are empty.
     */
    public static String ipFrom(HttpServerRequest request) {
        // X-Forwarded-For: client1, proxy1, proxy2
        String ip = StringUtils.trimToNull(
                StringUtils.substringBefore(request.headers().get("X-Forwarded-For"), ","));
        if (ip == null) {
            ip = StringUtils.trimToNull(request.headers().get("X-Real-IP"));
        }
        if (ip == null) {
            ip = StringUtils.trimToNull(request.remoteAddress().host());
        }
        return ip;
    }

    public static String getDomainFromUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        try {
            return new URL(url).getHost();
        } catch (MalformedURLException e) {
            return null;
        }
    }
}
