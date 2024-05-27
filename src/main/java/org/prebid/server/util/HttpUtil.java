package org.prebid.server.util;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * This class consists of {@code static} utility methods for operating HTTP requests.
 */
public final class HttpUtil {

    private static final Logger logger = LoggerFactory.getLogger(HttpUtil.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    public static final String APPLICATION_JSON_CONTENT_TYPE =
            HttpHeaderValues.APPLICATION_JSON + ";" + HttpHeaderValues.CHARSET + "="
                    + StandardCharsets.UTF_8.toString().toLowerCase();

    public static final CharSequence X_FORWARDED_FOR_HEADER = HttpHeaders.createOptimized("X-Forwarded-For");
    public static final CharSequence X_REAL_IP_HEADER = HttpHeaders.createOptimized("X-Real-Ip");
    public static final CharSequence DNT_HEADER = HttpHeaders.createOptimized("DNT");
    public static final CharSequence ORIGIN_HEADER = HttpHeaders.createOptimized("Origin");
    public static final CharSequence ACCEPT_HEADER = HttpHeaders.createOptimized("Accept");
    public static final CharSequence SEC_GPC_HEADER = HttpHeaders.createOptimized("Sec-GPC");
    public static final CharSequence SEC_BROWSING_TOPICS_HEADER = HttpHeaders.createOptimized("Sec-Browsing-Topics");
    public static final CharSequence OBSERVE_BROWSING_TOPICS_HEADER =
            HttpHeaders.createOptimized("Observe-Browsing-Topics");
    public static final CharSequence CONTENT_TYPE_HEADER = HttpHeaders.createOptimized("Content-Type");
    public static final CharSequence X_REQUESTED_WITH_HEADER = HttpHeaders.createOptimized("X-Requested-With");
    public static final CharSequence REFERER_HEADER = HttpHeaders.createOptimized("Referer");
    public static final CharSequence USER_AGENT_HEADER = HttpHeaders.createOptimized("User-Agent");
    public static final CharSequence COOKIE_HEADER = HttpHeaders.createOptimized("Cookie");
    public static final CharSequence SEC_COOKIE_DEPRECATION =
            HttpHeaders.createOptimized("Sec-Cookie-Deprecation");

    public static final CharSequence ACCEPT_LANGUAGE_HEADER = HttpHeaders.createOptimized("Accept-Language");
    public static final CharSequence SET_COOKIE_HEADER = HttpHeaders.createOptimized("Set-Cookie");
    public static final CharSequence AUTHORIZATION_HEADER = HttpHeaders.createOptimized("Authorization");
    public static final CharSequence CACHE_CONTROL_HEADER = HttpHeaders.createOptimized("Cache-Control");
    public static final CharSequence EXPIRES_HEADER = HttpHeaders.createOptimized("Expires");
    public static final CharSequence PRAGMA_HEADER = HttpHeaders.createOptimized("Pragma");
    public static final CharSequence LOCATION_HEADER = HttpHeaders.createOptimized("Location");
    public static final CharSequence CONNECTION_HEADER = HttpHeaders.createOptimized("Connection");
    public static final CharSequence CONTENT_ENCODING_HEADER = HttpHeaders.createOptimized("Content-Encoding");
    public static final CharSequence X_OPENRTB_VERSION_HEADER = HttpHeaders.createOptimized("x-openrtb-version");
    public static final CharSequence X_PREBID_HEADER = HttpHeaders.createOptimized("x-prebid");
    private static final Set<String> SENSITIVE_HEADERS = Set.of(AUTHORIZATION_HEADER.toString());

    //the low-entropy client hints
    public static final CharSequence SAVE_DATA = HttpHeaders.createOptimized("Save-Data");
    public static final CharSequence SEC_CH_UA = HttpHeaders.createOptimized("Sec-CH-UA");
    public static final CharSequence SEC_CH_UA_MOBILE = HttpHeaders.createOptimized("Sec-CH-UA-Mobile");
    public static final CharSequence SEC_CH_UA_PLATFORM = HttpHeaders.createOptimized("Sec-CH-UA-Platform");
    public static final String MACROS_OPEN = "{{";
    public static final String MACROS_CLOSE = "}}";

    private HttpUtil() {
    }

    /**
     * Checks the input string for using as URL.
     */
    public static String validateUrl(String url) {
        if (containsMacrosses(url)) {
            return url;
        }

        try {
            return new URL(url).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("URL supplied is not valid: " + url, e);
        }
    }

    // TODO: We need our own way to work with url macrosses
    private static boolean containsMacrosses(String url) {
        return StringUtils.contains(url, MACROS_OPEN) && StringUtils.contains(url, MACROS_CLOSE);
    }

    /**
     * Returns encoded URL for the given value.
     * <p>
     * The result can be safety used as the query string.
     */
    public static String encodeUrl(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * Returns decoded value if supplied is not null, otherwise returns null.
     */
    public static String decodeUrl(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
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

    public static String getHostFromUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        try {
            return new URL(url).getHost();
        } catch (MalformedURLException e) {
            return null;
        }
    }

    public static Map<String, String> cookiesAsMap(HttpRequestContext httpRequest) {
        final String cookieHeader = httpRequest.getHeaders().get(HttpHeaders.COOKIE);
        if (cookieHeader == null) {
            return Collections.emptyMap();
        }

        return ServerCookieDecoder.STRICT.decode(cookieHeader).stream()
                .collect(Collectors.toMap(
                        io.netty.handler.codec.http.cookie.Cookie::name,
                        io.netty.handler.codec.http.cookie.Cookie::value));

    }

    public static Map<String, String> cookiesAsMap(RoutingContext routingContext) {
        return routingContext.cookieMap().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getValue()));
    }

    public static String createCookiesHeader(RoutingContext routingContext) {
        return routingContext.cookieMap().entrySet().stream()
                .map(entry -> Cookie.cookie(entry.getKey(), entry.getValue().getValue()))
                .map(Cookie::encode)
                .collect(Collectors.joining("; "));
    }

    public static boolean executeSafely(RoutingContext routingContext,
                                        Endpoint endpoint,
                                        Consumer<HttpServerResponse> responseConsumer) {

        return executeSafely(routingContext, endpoint.value(), responseConsumer);
    }

    public static boolean executeSafely(RoutingContext routingContext,
                                        String endpoint,
                                        Consumer<HttpServerResponse> responseConsumer) {

        final HttpServerResponse response = routingContext.response();

        if (response.closed()) {
            conditionalLogger
                    .warn("Client already closed connection, response to %s will be skipped".formatted(endpoint), 0.01);
            return false;
        }

        try {
            responseConsumer.accept(response);
            return true;
        } catch (Exception e) {
            logger.warn("Failed to send {} response: {}", endpoint, e.getMessage());
            return false;
        }
    }

    /**
     * Converts {@link MultiMap} headers format to Map, where keys are headers names and values are lists
     * of header's values
     */
    public static Map<String, List<String>> toDebugHeaders(MultiMap headers) {
        return headers != null
                ? headers.entries().stream()
                .filter(entry -> !isSensitiveHeader(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey,
                        entry -> StringUtils.isNotBlank(entry.getValue())
                                ? Arrays.stream(entry.getValue().split(","))
                                .map(String::trim)
                                .toList()
                                : Collections.singletonList(entry.getValue())))
                : null;
    }

    private static boolean isSensitiveHeader(String header) {
        return SENSITIVE_HEADERS.stream().anyMatch(header::equalsIgnoreCase);
    }
}
