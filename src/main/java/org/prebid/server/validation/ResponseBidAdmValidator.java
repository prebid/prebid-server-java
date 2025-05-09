package org.prebid.server.validation;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

public class ResponseBidAdmValidator {

    private static final String[] SECURE_MARKUP_MARKERS = {"https:", "https%3A"};

    private final Collection<String> allowedPaths;

    public ResponseBidAdmValidator(Collection<String> allowedPaths) {
        this.allowedPaths = CollectionUtils.emptyIfNull(allowedPaths);
    }

    public boolean isSecure(String adm) {
        if (!StringUtils.containsAny(adm, SECURE_MARKUP_MARKERS)) {
            return false;
        }

        final Set<String> encodedAllowedPaths = allowedPaths.stream()
                .map(pattern -> URLEncoder.encode(pattern, StandardCharsets.UTF_8))
                .collect(Collectors.toSet());

        return allUrlsAllowed(adm, allowedPaths, "http:", "//")
                && allUrlsAllowed(adm, encodedAllowedPaths, "http%3A", "%2F%2F");
    }

    private static boolean allUrlsAllowed(String adm,
                                          Collection<String> allowedPaths,
                                          String protocol,
                                          String doubleSlash) {

        int searchStartIndex = 0;
        while (searchStartIndex < adm.length()) {
            int httpIndex = adm.indexOf(protocol, searchStartIndex);

            if (httpIndex == -1) {
                return true;
            }

            int afterHttpPrefixIndex = httpIndex + protocol.length();
            if (afterHttpPrefixIndex + 1 > adm.length()) {
                return true;
            }

            if (!adm.startsWith(doubleSlash, afterHttpPrefixIndex)) {
                searchStartIndex = httpIndex + 1;
                continue;
            }

            int afterHttpDoubleSlashIndex = afterHttpPrefixIndex + doubleSlash.length();
            if (afterHttpDoubleSlashIndex + 1 > adm.length()) {
                return true;
            }

            boolean isAllowedExactPathMatch = allowedPaths.stream()
                    .anyMatch(allowedPattern -> adm.startsWith(allowedPattern, afterHttpDoubleSlashIndex));

            if (!isAllowedExactPathMatch) {
                return false;
            }

            searchStartIndex = httpIndex + 1;
        }

        return false;
    }
}
