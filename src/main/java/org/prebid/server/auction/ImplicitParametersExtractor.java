package org.prebid.server.auction;

import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import io.vertx.core.MultiMap;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.util.HttpUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

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
    public String refererFrom(HttpRequestContext request) {
        final String urlOverride = request.getQueryParams().get("url_override");
        final String url = StringUtils.isNotBlank(urlOverride) ? urlOverride
                : StringUtils.trimToNull(request.getHeaders().get(HttpUtil.REFERER_HEADER));

        return StringUtils.isNotBlank(url) && !StringUtils.startsWith(url, "http")
                ? "http://" + url
                : url;
    }

    /**
     * Determines top-level domain of the passed host name.
     *
     * @throws PreBidException if top level domain could not be derived from the host name
     */
    public String domainFrom(String host) throws PreBidException {
        if (StringUtils.isBlank(host)) {
            throw new PreBidException("Host is not defined or can not be derived from request");
        }

        final String domain = psl.getRegistrableDomain(host);

        if (domain == null) {
            // null means effective top level domain plus one couldn't be derived
            throw new PreBidException("Cannot derive eTLD+1 for host " + host);
        }

        return domain;
    }

    /**
     * Determines IP-Address candidates by checking http headers and remote host address.
     */
    public List<String> ipFrom(CaseInsensitiveMultiMap headers, String host) {
        return ipFrom(headers::get, host);
    }

    public List<String> ipFrom(MultiMap headers, String host) {
        return ipFrom(headers::get, host);
    }

    private List<String> ipFrom(Function<String, String> headerGetter, String host) {
        final List<String> candidates = new ArrayList<>();
        candidates.add(headerGetter.apply(HttpUtil.TRUE_CLIENT_IP_HEADER.toString()));
        final String xff = headerGetter.apply(HttpUtil.X_FORWARDED_FOR_HEADER.toString());
        if (xff != null) {
            candidates.addAll(Arrays.asList(xff.split(",")));
        }
        candidates.add(headerGetter.apply(HttpUtil.X_REAL_IP_HEADER.toString()));
        candidates.add(headerGetter.apply(HttpUtil.X_DEVICE_IP_HEADER.toString()));
        candidates.add(host);

        return candidates.stream()
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * Determines User-Agent by checking 'User-Agent' http header.
     */
    public String uaFrom(HttpRequestContext request) {
        return Optional.ofNullable(getTrimmedHeader(request, HttpUtil.USER_AGENT_HEADER))
                .orElseGet(() -> getTrimmedHeader(request, HttpUtil.X_DEVICE_USER_AGENT_HEADER));
    }

    private static String getTrimmedHeader(HttpRequestContext request, CharSequence header) {
        return StringUtils.trimToNull(request.getHeaders().get(header));
    }

    /**
     * Determines the value of 'secure' flag by checking if 'X-Forwarded-Proto' contains 'value' or if HTTP request
     * scheme is 'https'. Returns 1 if one of these conditions evaluates to true or null otherwise.
     */
    public Integer secureFrom(HttpRequestContext httpRequest) {
        return StringUtils.equalsIgnoreCase(httpRequest.getHeaders().get("X-Forwarded-Proto"), "https")
                || StringUtils.equalsIgnoreCase(httpRequest.getScheme(), "https")
                ? 1 : null;
    }

    public String gpcFrom(HttpRequestContext httpRequest) {
        final String gpcAsString = httpRequest.getHeaders().get(HttpUtil.SEC_GPC_HEADER);
        return "1".equals(gpcAsString) || "\"1\"".equals(gpcAsString) ? "1" : null;
    }
}
