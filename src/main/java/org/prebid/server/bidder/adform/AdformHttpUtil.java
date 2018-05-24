package org.prebid.server.bidder.adform;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.adform.model.UrlParameters;
import org.prebid.server.util.HttpUtil;

import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Util class to help {@link org.prebid.server.bidder.adform.AdformBidder} and
 * {@link org.prebid.server.bidder.adform.AdformAdapter} to create adform url and headers
 */
public class AdformHttpUtil {

    private static final String PRICE_TYPE_GROSS = "gross";
    private static final String PRICE_TYPE_NET = "net";
    private static final String PRICE_TYPE_GROSS_PARAM = String.format("&pt=%s", PRICE_TYPE_GROSS);
    private static final String PRICE_TYPE_NET_PARAM = String.format("&pt=%s", PRICE_TYPE_NET);

    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    private AdformHttpUtil() {
    }

    /**
     * Creates headers for Adform request
     */
    public static MultiMap buildAdformHeaders(String version, String userAgent, String ip,
                                              String referer, String userId) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .add(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .add(HttpHeaders.USER_AGENT, userAgent)
                .add(HttpUtil.X_FORWARDED_FOR_HEADER, ip)
                .add(HttpUtil.X_REQUEST_AGENT_HEADER, String.format("PrebidAdapter %s", version));

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
    public static String buildAdformUrl(UrlParameters parameters) {
        final String mids = parameters.getMasterTagIds().stream()
                .map(masterTagId -> String.format("mid=%s", masterTagId))
                .map(mid -> Base64.getUrlEncoder().withoutPadding().encodeToString(mid.getBytes()))
                .collect(Collectors.joining("&"));
        final String advertisingId = parameters.getAdvertisingId();
        final String adid = StringUtils.isNotEmpty(advertisingId) ? String.format("&adid=%s", advertisingId) : "";
        final String endpointUrl = parameters.getEndpointUrl();
        final String priceTypeParameter = getValidPriceTypeParameter(parameters.getPriceTypes());
        final String uri = parameters.isSecure() ? endpointUrl.replaceFirst("http://", "https://") : endpointUrl;
        return String.format("%s/?CC=1&rp=4&fd=1&stid=%s&ip=%s%s%s&%s", uri, parameters.getTid(), parameters.getIp(),
                adid, priceTypeParameter, mids);
    }

    /**
     * Returns price type parameter if valid is found. Otherwise returns empty string.
     */
    private static String getValidPriceTypeParameter(List<String> priceTypes) {
        String priceTypeParameter = "";
        for (final String priceType : priceTypes) {
            final String lowCasePriceType = priceType != null ? priceType.toLowerCase() : null;
            if (isPriceTypeValid(lowCasePriceType)) {
                if (lowCasePriceType.equals(PRICE_TYPE_GROSS)) {
                    priceTypeParameter = PRICE_TYPE_GROSS_PARAM;
                    break;
                } else {
                    priceTypeParameter = PRICE_TYPE_NET_PARAM;
                }
            }
        }
        return priceTypeParameter;
    }

    /**
     * Checks if price type is valid value
     */
    private static boolean isPriceTypeValid(String priceType) {
        return priceType != null && (priceType.equals(PRICE_TYPE_GROSS) || priceType.equals(PRICE_TYPE_NET));
    }
}
