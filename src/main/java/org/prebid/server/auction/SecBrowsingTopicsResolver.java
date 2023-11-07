package org.prebid.server.auction;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.SecBrowsingTopic;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.util.HttpUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SecBrowsingTopicsResolver {

    private static final String TOPIC_DOMAIN = "google.com";
    private static final Pattern FIELD_PATTERN =
            Pattern.compile("\\((\\s*\\d+[\\d\\s]*)\\);v=chrome\\.(?:(?!:)\\S)+:([1-9]|10):(\\S+)");
    private static final int FIELDS_LIMIT = 10;

    public List<SecBrowsingTopic> resolve(CaseInsensitiveMultiMap headers) {
        final String secBrowserTopics = headers.get(HttpUtil.SEC_BROWSING_TOPICS_HEADER);
        if (StringUtils.isBlank(secBrowserTopics)) {
            return Collections.emptyList();
        }

        return fields(secBrowserTopics)
                .map(FIELD_PATTERN::matcher)
                .filter(Matcher::matches)
                .map(SecBrowsingTopicsResolver::toSecBrowsingTopic)
                .toList();
    }

    private static Stream<String> fields(String fields) {
        return Arrays.stream(fields.split(",", FIELDS_LIMIT + 1))
                .limit(FIELDS_LIMIT)
                .map(StringUtils::trimToEmpty);
    }

    private static SecBrowsingTopic toSecBrowsingTopic(MatchResult matchResult) {
        return SecBrowsingTopic.of(
                TOPIC_DOMAIN,
                parseSegments(matchResult.group(1)),
                parseInt(matchResult.group(2)),
                matchResult.group(3));
    }

    private static Set<String> parseSegments(String segments) {
        return Arrays.stream(segments.split(" "))
                .map(StringUtils::trim)
                .filter(StringUtils::isNotEmpty)
                .map(SecBrowsingTopicsResolver::parseInt)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static int parseInt(String integer) {
        try {
            return Integer.parseInt(integer);
        } catch (NumberFormatException e) {
            throw new PreBidException(e.getMessage());
        }
    }
}
