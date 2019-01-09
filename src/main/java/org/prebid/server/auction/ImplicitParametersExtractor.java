package org.prebid.server.auction;

import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import io.vertx.core.http.HttpServerRequest;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.util.HttpUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

/**
 * Convenient place to keep utilities for extracting parameters from HTTP request with intention to use them in auction.
 */
public class ImplicitParametersExtractor {

    private final PublicSuffixList psl;

    public ImplicitParametersExtractor(PublicSuffixList psl) {
        this.psl = Objects.requireNonNull(psl);
    }

    /**
     * Determines Referer by checking 'url_override' request parameter, or if it's empty 'Referer' header. Then if
     * result is not blank and missing 'http://' prefix appends it.
     */
    public String refererFrom(HttpServerRequest request) {
        final String urlOverride = request.getParam("url_override");
        final String url = StringUtils.isNotBlank(urlOverride) ? urlOverride
                : StringUtils.trimToNull(request.headers().get(HttpUtil.REFERER_HEADER));

        return StringUtils.isNotBlank(url) && !StringUtils.startsWith(url, "http")
                ? String.format("http://%s", url)
                : url;
    }

    /**
     * Determines domain the address refers to.
     *
     * @throws PreBidException if address does not represent valid URL, host could not be found in URL or top level
     *                         domain could not be derived from the host
     */
    public String domainFrom(String urlString) throws PreBidException {
        final URL url;
        try {
            url = new URL(urlString);
        } catch (MalformedURLException e) {
            throw new PreBidException(String.format("Invalid URL '%s': %s", urlString, e.getMessage()), e);
        }

        final String host = url.getHost();
        if (StringUtils.isBlank(host)) {
            throw new PreBidException(String.format("Host not found from URL '%s'", url.toString()));
        }

        final String domain = psl.getRegistrableDomain(host);

        if (domain == null) {
            // null means effective top level domain plus one couldn't be derived
            throw new PreBidException(
                    String.format("Invalid URL '%s': cannot derive eTLD+1 for domain %s", host, host));
        }

        return domain;
    }

    /**
     * Determines IP-Address by checking "X-Forwarded-For", "X-Real-IP" http headers or remote host address
     * if both are empty.
     */
    public String ipFrom(HttpServerRequest request) {
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

    /**
     * Determines User-Agent by checking 'User-Agent' http header.
     */
    public String uaFrom(HttpServerRequest request) {
        return StringUtils.trimToNull(request.headers().get(HttpUtil.USER_AGENT_HEADER));
    }

    /**
     * Determines the value of 'secure' flag by checking if 'X-Forwarded-Proto' contains 'value' or if HTTP request
     * scheme is 'https'. Returns 1 if one of these conditions evaluates to true or null otherwise.
     */
    public Integer secureFrom(HttpServerRequest httpRequest) {
        return StringUtils.equalsIgnoreCase(httpRequest.headers().get("X-Forwarded-Proto"), "https")
                || StringUtils.equalsIgnoreCase(httpRequest.scheme(), "https")
                ? 1 : null;
    }
}
