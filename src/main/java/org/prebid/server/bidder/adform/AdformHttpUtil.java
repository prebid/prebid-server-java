package org.prebid.server.bidder.adform;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.adform.model.AdformDigitrust;
import org.prebid.server.bidder.adform.model.UrlParameters;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Util class to help {@link org.prebid.server.bidder.adform.AdformBidder} and
 * {@link org.prebid.server.bidder.adform.AdformAdapter} to create adform url and headers
 */
class AdformHttpUtil {

    private static final String PRICE_TYPE_GROSS = "gross";
    private static final String PRICE_TYPE_NET = "net";
    private static final String PRICE_TYPE_GROSS_PARAM = String.format("pt=%s", PRICE_TYPE_GROSS);
    private static final String PRICE_TYPE_NET_PARAM = String.format("pt=%s", PRICE_TYPE_NET);

    private static final String APPLICATION_JSON =
            HttpHeaderValues.APPLICATION_JSON.toString() + ";" + HttpHeaderValues.CHARSET.toString() + "=" + "utf-8";

    private AdformHttpUtil() {
    }

    /**
     * Creates headers for Adform request
     */
    static MultiMap buildAdformHeaders(String version, String userAgent, String ip,
                                       String referer, String userId, AdformDigitrust adformDigitrust) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                .add(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                .add(HttpHeaders.USER_AGENT, userAgent)
                .add(HttpUtil.X_FORWARDED_FOR_HEADER, ip)
                .add(HttpUtil.X_REQUEST_AGENT_HEADER, String.format("PrebidAdapter %s", version));

        if (StringUtils.isNotEmpty(referer)) {
            headers.add(HttpHeaders.REFERER, referer);
        }
        final List<String> cookieValues = new ArrayList<>();
        if (StringUtils.isNotEmpty(userId)) {
            cookieValues.add(String.format("uid=%s", userId));
        }

        if (adformDigitrust != null) {
            try {
                final String adformDigitrustEncoded = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(Json.mapper.writeValueAsString(adformDigitrust).getBytes());
                // Cookie name and structure are described here:
                // https://github.com/digi-trust/dt-cdn/wiki/Cookies-for-Platforms
                cookieValues.add(String.format("DigiTrust.v1.identity=%s", adformDigitrustEncoded));
            } catch (JsonProcessingException e) {
                // do not add digitrust to cookie header and just ignore this exception
            }
        }

        if (CollectionUtils.isNotEmpty(cookieValues)) {
            headers.add(HttpHeaders.COOKIE, cookieValues.stream().collect(Collectors.joining(";")));
        }

        return headers;
    }

    /**
     * Creates url with parameters for adform request
     */
    static String buildAdformUrl(UrlParameters parameters) {
        final List<String> params = new ArrayList<>();

        final String advertisingId = parameters.getAdvertisingId();
        if (StringUtils.isNotEmpty(advertisingId)) {
            params.add(String.format("adid=%s", advertisingId));
        }

        params.add("CC=1");
        params.add("rp=4");
        params.add("fd=1");
        params.add("stid=" + parameters.getTid());
        params.add("ip=" + parameters.getIp());

        final String priceTypeParameter = getValidPriceTypeParameter(parameters.getPriceTypes());
        if (StringUtils.isNotEmpty(priceTypeParameter)) {
            params.add(priceTypeParameter);
        }

        params.add("gdpr=" + parameters.getGdprApplies());
        params.add("gdpr_consent=" + parameters.getConsent());

        final String mids = parameters.getMasterTagIds().stream()
                .map(masterTagId -> String.format("mid=%s", masterTagId))
                .map(mid -> Base64.getUrlEncoder().withoutPadding().encodeToString(mid.getBytes()))
                .collect(Collectors.joining("&"));

        final String urlParams = params.stream().sorted().collect(Collectors.joining("&"));

        final String endpointUrl = parameters.getEndpointUrl();
        final String uri = parameters.isSecure() ? endpointUrl.replaceFirst("http://", "https://") : endpointUrl;

        return String.format("%s?%s&%s", uri, urlParams, mids);
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
