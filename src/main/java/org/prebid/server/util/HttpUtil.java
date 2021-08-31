package org.prebid.server.util;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;
import io.vertx.core.MultiMap;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.Endpoint;
import org.prebid.server.model.HttpRequestContext;

import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
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
    public static final CharSequence DNT_HEADER = HttpHeaders.createOptimized("DNT");
    public static final CharSequence X_REQUEST_AGENT_HEADER = HttpHeaders.createOptimized("X-Request-Agent");
    public static final CharSequence ORIGIN_HEADER = HttpHeaders.createOptimized("Origin");
    public static final CharSequence ACCEPT_HEADER = HttpHeaders.createOptimized("Accept");
    public static final CharSequence SEC_GPC_HEADER = HttpHeaders.createOptimized("Sec-GPC");
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
    public static final CharSequence CONNECTION_HEADER = HttpHeaders.createOptimized("Connection");
    public static final CharSequence ACCEPT_ENCODING_HEADER = HttpHeaders.createOptimized("Accept-Encoding");
    public static final CharSequence X_OPENRTB_VERSION_HEADER = HttpHeaders.createOptimized("x-openrtb-version");
    public static final CharSequence X_PREBID_HEADER = HttpHeaders.createOptimized("x-prebid");
    private static final Set<String> SENSITIVE_HEADERS = new HashSet<>(Arrays.asList(AUTHORIZATION_HEADER.toString()));
    public static final CharSequence PG_TRX_ID = HttpHeaders.createOptimized("pg-trx-id");

    private static final String BASIC_AUTH_PATTERN = "Basic %s";

    private HttpUtil() {
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

    public static ZonedDateTime getDateFromHeader(MultiMap headers, String header) {
        return getDateFromHeader(headers::get, header);
    }

    public static ZonedDateTime getDateFromHeader(CaseInsensitiveMultiMap headers, String header) {
        return getDateFromHeader(headers::get, header);
    }

    private static ZonedDateTime getDateFromHeader(Function<String, String> headerGetter, String header) {
        final String isoTimeStamp = headerGetter.apply(header);
        if (isoTimeStamp == null) {
            return null;
        }

        try {
            return ZonedDateTime.parse(isoTimeStamp);
        } catch (Exception e) {
            throw new PreBidException(String.format("%s header is not compatible to ISO-8601 format: %s",
                    header, isoTimeStamp));
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

    public static String toSetCookieHeaderValue(Cookie cookie) {
        return String.join("; ", cookie.encode(), "SameSite=None; Secure");
    }

    public static boolean executeSafely(RoutingContext routingContext, Endpoint endpoint,
                                        Consumer<HttpServerResponse> responseConsumer) {
        return executeSafely(routingContext, endpoint.value(), responseConsumer);
    }

    public static boolean executeSafely(RoutingContext routingContext, String endpoint,
                                        Consumer<HttpServerResponse> responseConsumer) {

        final HttpServerResponse response = routingContext.response();

        if (response.closed()) {
            conditionalLogger.warn(
                    String.format("Client already closed connection, response to %s will be skipped", endpoint),
                    0.01);
            return false;
        }

        try {
            responseConsumer.accept(response);
            return true;
        } catch (Throwable e) {
            logger.warn("Failed to send {0} response: {1}", endpoint, e.getMessage());
            return false;
        }
    }

    /**
     * Creates standart basic auth header value
     */
    public static String makeBasicAuthHeaderValue(String username, String password) {
        return String.format(BASIC_AUTH_PATTERN, Base64.getEncoder().encodeToString((username + ':' + password)
                .getBytes()));
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
                                .collect(Collectors.toList())
                                : Collections.singletonList(entry.getValue())))
                : null;
    }

    private static boolean isSensitiveHeader(String header) {
        return SENSITIVE_HEADERS.stream().anyMatch(header::equalsIgnoreCase);
    }
}
