package org.prebid.server.util;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * This class consists of {@code static} utility methods for operating HTTP requests.
 */
public final class HttpUtil {

    public static final String APPLICATION_JSON_CONTENT_TYPE =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "="
                    + StandardCharsets.UTF_8.toString().toLowerCase();

    public static final CharSequence X_FORWARDED_FOR_HEADER = HttpHeaders.createOptimized("X-Forwarded-For");
    public static final CharSequence DNT_HEADER = HttpHeaders.createOptimized("DNT");
    public static final CharSequence X_REQUEST_AGENT_HEADER = HttpHeaders.createOptimized("X-Request-Agent");
    public static final CharSequence ORIGIN_HEADER = HttpHeaders.createOptimized("Origin");
    public static final CharSequence ACCEPT_HEADER = HttpHeaders.createOptimized("Accept");
    public static final CharSequence CONTENT_TYPE_HEADER = HttpHeaders.createOptimized("Content-Type");
    public static final CharSequence X_REQUESTED_WITH_HEADER = HttpHeaders.createOptimized("X-Requested-With");
    public static final CharSequence REFERER_HEADER = HttpHeaders.createOptimized("Referer");
    public static final CharSequence USER_AGENT_HEADER = HttpHeaders.createOptimized("User-Agent");
    public static final CharSequence COOKIE_HEADER = HttpHeaders.createOptimized("Cookie");
    public static final CharSequence ACCEPT_LANGUAGE_HEADER = HttpHeaders.createOptimized("Accept-Language");
    public static final CharSequence SET_COOKIE_HEADER = HttpHeaders.createOptimized("Set-Cookie");
    public static final CharSequence AUTHORIZATION_HEADER = HttpHeaders.createOptimized("Authorization");
    public static final CharSequence DATE_HEADER = HttpHeaders.createOptimized("Date");
    public static final CharSequence CACHE_CONTROL_HEADER = HttpHeaders.createOptimized("Cache-Control");
    public static final CharSequence EXPIRES_HEADER = HttpHeaders.createOptimized("Expires");
    public static final CharSequence PRAGMA_HEADER = HttpHeaders.createOptimized("Pragma");
    public static final CharSequence LOCATION_HEADER = HttpHeaders.createOptimized("Location");

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
    public static String encodeUrl(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(String.format("Cannot encode url: %s", value));
        }
    }

    /**
     * Returns decoded value if supplied is not null, otherwise returns null.
     */
    public static String decodeUrl(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(String.format("Cannot decode url: %s", value));
        }
    }

    /**
     * Creates general headers for request.
     */
    public static MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(CONTENT_TYPE_HEADER, APPLICATION_JSON_CONTENT_TYPE)
                .add(ACCEPT_HEADER, HttpHeaderValues.APPLICATION_JSON);
    }

    /**
     * Creates header from name and value, when value is not null or empty string.
     */
    public static void addHeaderIfValueIsNotEmpty(MultiMap headers, CharSequence headerName, CharSequence headerValue) {
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

    public static Map<String, String> cookiesAsMap(RoutingContext context) {
        return context.cookies().stream()
                .collect(Collectors.toMap(Cookie::getName, Cookie::getValue));
    }

    public static String toSetCookieHeaderValue(Cookie cookie) {
        return String.join("; ", cookie.encode(), "SameSite=none");
    }
}
