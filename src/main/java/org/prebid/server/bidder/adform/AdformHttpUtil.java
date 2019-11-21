package org.prebid.server.bidder.adform;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.adform.model.AdformDigitrust;
import org.prebid.server.bidder.adform.model.UrlParameters;
import org.prebid.server.json.EncodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
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

    private final JacksonMapper mapper;

    AdformHttpUtil(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Creates headers for Adform request
     */
    MultiMap buildAdformHeaders(String version,
                                String userAgent,
                                String ip,
                                String referer,
                                String userId,
                                AdformDigitrust adformDigitrust) {

        final MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE)
                .add(HttpUtil.ACCEPT_HEADER, HttpHeaderValues.APPLICATION_JSON)
                .add(HttpUtil.USER_AGENT_HEADER, userAgent)
                .add(HttpUtil.X_FORWARDED_FOR_HEADER, ip)
                .add(HttpUtil.X_REQUEST_AGENT_HEADER, String.format("PrebidAdapter %s", version));

        if (StringUtils.isNotEmpty(referer)) {
            headers.add(HttpUtil.REFERER_HEADER, referer);
        }
        final List<String> cookieValues = new ArrayList<>();
        if (StringUtils.isNotEmpty(userId)) {
            cookieValues.add(String.format("uid=%s", userId));
        }

        if (adformDigitrust != null) {
            try {
                final String adformDigitrustEncoded = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(mapper.encode(adformDigitrust).getBytes());
                // Cookie name and structure are described here:
                // https://github.com/digi-trust/dt-cdn/wiki/Cookies-for-Platforms
                cookieValues.add(String.format("DigiTrust.v1.identity=%s", adformDigitrustEncoded));
            } catch (EncodeException e) {
                // do not add digitrust to cookie header and just ignore this exception
            }
        }

        if (CollectionUtils.isNotEmpty(cookieValues)) {
            headers.add(HttpUtil.COOKIE_HEADER, String.join(";", cookieValues));
        }

        return headers;
    }

    /**
     * Creates url with parameters for adform request
     */
    String buildAdformUrl(UrlParameters parameters) {
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

        final List<String> encodedMids = new ArrayList<>();
        final List<Long> masterTagIds = parameters.getMasterTagIds();
        final List<String> keyValues = parameters.getKeyValues();
        final List<String> keyWords = parameters.getKeyWords();
        for (int i = 0; i < masterTagIds.size(); i++) {
            final StringBuilder mid = new StringBuilder(
                    String.format("mid=%s&rcur=%s", masterTagIds.get(i), parameters.getCurrency()));

            if (CollectionUtils.isNotEmpty(keyValues)) {
                final String keyValue = keyValues.get(i);
                if (StringUtils.isNotBlank(keyValue)) {
                    mid.append(String.format("&mkv=%s", keyValue));
                }
            }

            if (CollectionUtils.isNotEmpty(keyWords)) {
                final String keyWord = keyWords.get(i);
                if (StringUtils.isNotBlank(keyWord)) {
                    mid.append(String.format("&mkw=%s", keyWord));
                }
            }

            encodedMids.add(Base64.getUrlEncoder().withoutPadding().encodeToString(mid.toString().getBytes()));
        }

        final String mids = String.join("&", encodedMids);

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
