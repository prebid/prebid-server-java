package org.prebid.server.bidder.adform;

import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import org.apache.commons.lang3.StringUtils;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Util class to help {@link org.prebid.server.bidder.adform.AdformBidder} and
 * {@link org.prebid.server.bidder.adform.AdformAdapter} to create adform url and headers
 */
public class AdformHttpUtil {

    private static final CharSequence X_REQUEST_AGENT = HttpHeaders.createOptimized("X-Request-Agent");
    private static final CharSequence X_FORWARDED_FOR = HttpHeaders.createOptimized("X-Forwarded-For");

    private AdformHttpUtil() {
    }

    /**
     * Creates headers for Adform request
     */
    public static MultiMap buildAdformHeaders(MultiMap headers, String version, String userAgent, String ip,
                                              String referer, String userId) {
        headers.add(HttpHeaders.USER_AGENT, userAgent)
                .add(X_FORWARDED_FOR, ip)
                .add(X_REQUEST_AGENT, String.format("PrebidAdapter %s", version));

        if (StringUtils.isNotEmpty(referer)) {
            headers.add(HttpHeaders.REFERER, referer);
        }

        if (StringUtils.isNotEmpty(userId)) {
            headers.add(HttpHeaders.COOKIE, String.format("uid=%s", userId));
        }
        return headers;
    }

    /**
     * Creates url with parameters for adform request
     */
    public static String buildAdformUrl(List<String> masterTagIds, String endpointUrl, String tid, Boolean secure) {

        final String mids = masterTagIds.stream()
                .map(masterTagId -> String.format("mid=%s", masterTagId))
                .map(mid -> Base64.getEncoder().withoutPadding().encodeToString(mid.getBytes()))
                .collect(Collectors.joining("&"));

        final String uri = secure ? endpointUrl.replace("http", "https") : endpointUrl;
        return String.format("%s/?CC=1&rp=4&fd=1&stid=%s&%s", uri, tid, mids);
    }
}
