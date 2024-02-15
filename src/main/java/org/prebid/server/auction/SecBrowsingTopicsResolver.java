package org.prebid.server.auction;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.SecBrowsingTopic;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.util.HttpUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SecBrowsingTopicsResolver {

    private static final String FIELDS_SEPARATOR = ",";
    private static final String PADDING_FIELD_PREFIX = "();p=";
    private static final Pattern FIELD_PATTERN =
            Pattern.compile("\\((\\s*\\d+[\\d\\s]*)\\);v=chrome\\.[^:\\s]+:([1-9]|10):([^:\\s]+)$");
    private static final int FIELDS_LIMIT = 10;

    private static final String SEGMENTS_SEPARATOR = " ";

    private final String topicsDomain;

    public SecBrowsingTopicsResolver(String topicsDomain) {
        this.topicsDomain = Objects.requireNonNull(topicsDomain);
    }

    public List<SecBrowsingTopic> resolve(CaseInsensitiveMultiMap headers,
                                          boolean debugEnabled,
                                          List<String> warnings) {

        final String secBrowserTopics = headers.get(HttpUtil.SEC_BROWSING_TOPICS_HEADER);
        if (StringUtils.isBlank(secBrowserTopics)) {
            return Collections.emptyList();
        }

        return fields(secBrowserTopics, debugEnabled, warnings)
                .filter(field -> !field.startsWith(PADDING_FIELD_PREFIX))
                .map(field -> toSecBrowsingTopic(field, debugEnabled, warnings))
                .filter(Objects::nonNull)
                .toList();
    }

    private static Stream<String> fields(String fields, boolean debugEnabled, List<String> warnings) {
        final Stream<String> baseStream = !debugEnabled
                ? Arrays.stream(fields.split(FIELDS_SEPARATOR, FIELDS_LIMIT + 1)).limit(FIELDS_LIMIT)
                : Arrays.stream(fields.split(FIELDS_SEPARATOR)).filter(limitAndLogDiscarded(warnings));

        return baseStream.map(StringUtils::trimToEmpty);
    }

    private static Predicate<String> limitAndLogDiscarded(List<String> warnings) {
        final AtomicInteger limit = new AtomicInteger(FIELDS_LIMIT);
        return field -> {
            final boolean skipFurther = limit.getAndDecrement() > 0;
            if (!skipFurther) {
                logWarning(warnings, true, field + " discarded due to limit reached.");
            }
            return skipFurther;
        };
    }

    private static void logWarning(List<String> warnings, boolean debugEnabled, String reason) {
        if (debugEnabled) {
            warnings.add("Invalid field in %s header: %s".formatted(HttpUtil.SEC_BROWSING_TOPICS_HEADER, reason));
        }
    }

    private SecBrowsingTopic toSecBrowsingTopic(String field, boolean debugEnabled, List<String> warnings) {
        final Matcher matcher = FIELD_PATTERN.matcher(field);
        if (!matcher.matches()) {
            logWarning(warnings, debugEnabled, field);
            return null;
        }

        try {
            return SecBrowsingTopic.of(
                    topicsDomain,
                    parseSegments(matcher.group(1)),
                    parseInt(matcher.group(2)),
                    matcher.group(3));
        } catch (PreBidException e) {
            logWarning(warnings, debugEnabled, field);
            return null;
        }
    }

    private static Set<String> parseSegments(String segments) {
        return Arrays.stream(StringUtils.trim(segments).split(SEGMENTS_SEPARATOR))
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
