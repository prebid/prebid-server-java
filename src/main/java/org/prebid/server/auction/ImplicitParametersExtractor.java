package org.prebid.server.auction;

import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import io.vertx.core.MultiMap;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.HttpRequestWrapper;
import org.prebid.server.util.HttpUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
    public String refererFrom(HttpRequestWrapper request) {
        final String urlOverride = request.getQueryParams().get("url_override");
        final String url = StringUtils.isNotBlank(urlOverride) ? urlOverride
                : StringUtils.trimToNull(request.getHeaders().get(HttpUtil.REFERER_HEADER));

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
     * Determines IP-Address candidates by checking http headers and remote host address.
     */
    public List<String> ipFrom(HttpRequestWrapper request) {
        final MultiMap headers = request.getHeaders();

        final List<String> candidates = new ArrayList<>();
        candidates.add(headers.get("True-Client-IP"));
        final String xff = headers.get("X-Forwarded-For");
        if (xff != null) {
            candidates.addAll(Arrays.asList(xff.split(",")));
        }
        candidates.add(headers.get("X-Real-IP"));
        candidates.add(request.getRemoteHost());

        return candidates.stream()
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Determines User-Agent by checking 'User-Agent' http header.
     */
    public String uaFrom(HttpRequestWrapper request) {
        return StringUtils.trimToNull(request.getHeaders().get(HttpUtil.USER_AGENT_HEADER));
    }

    /**
     * Determines the value of 'secure' flag by checking if 'X-Forwarded-Proto' contains 'value' or if HTTP request
     * scheme is 'https'. Returns 1 if one of these conditions evaluates to true or null otherwise.
     */
    public Integer secureFrom(HttpRequestWrapper httpRequest) {
        return StringUtils.equalsIgnoreCase(httpRequest.getHeaders().get("X-Forwarded-Proto"), "https")
                || StringUtils.equalsIgnoreCase(httpRequest.getScheme(), "https")
                ? 1 : null;
    }
}
