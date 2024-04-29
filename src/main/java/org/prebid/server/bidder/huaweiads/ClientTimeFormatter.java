package org.prebid.server.bidder.huaweiads;

import org.apache.commons.lang3.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.regex.Pattern;

public class ClientTimeFormatter {

    private static final DateTimeFormatter TIME_INPUT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter TIME_OUTPUT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ");
    private static final Pattern CLIENT_TIME_PATTERN_WITH_OFFSET =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]{1}\\d{4}$");
    private static final Pattern CLIENT_TIME_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}$");

    private final Clock clock;

    public ClientTimeFormatter(Clock clock) {
        this.clock = Objects.requireNonNull(clock);
    }

    //output format: 2006-01-02 15:04:05.000+0200
    public String format(String clientTime) {
        if (matchesPattern(clientTime, CLIENT_TIME_PATTERN_WITH_OFFSET)) {
            return clientTime;
        }

        final LocalDateTime time = matchesPattern(clientTime, CLIENT_TIME_PATTERN)
                ? LocalDateTime.parse(clientTime, TIME_INPUT_FORMATTER)
                : LocalDateTime.now(clock);

        final ZoneOffset zoneOffset = OffsetDateTime.now(clock).getOffset();
        return time.atZone(zoneOffset).format(TIME_OUTPUT_FORMATTER);
    }

    private static boolean matchesPattern(String clientTime, Pattern pattern) {
        return StringUtils.isNotBlank(clientTime) && pattern.matcher(clientTime).matches();
    }

    public String now() {
        return OffsetDateTime.now(clock).format(TIME_OUTPUT_FORMATTER);
    }

}
