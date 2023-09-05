package org.prebid.server.bidder.huaweiads;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

class ClientTimeFormatter {

    private static final DateTimeFormatter TIME_INPUT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter TIME_OUTPUT_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ");
    private static final Pattern CLIENT_TIME_PATTERN_WITH_OFFSET =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]{1}\\d{4}$");
    private static final Pattern CLIENT_TIME_PATTERN =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}$");

    private ClientTimeFormatter() {

    }

    //output format: 2006-01-02 15:04:05.000+0200
    public static String format(String clientTime) {
        if (StringUtils.isNotBlank(clientTime) && CLIENT_TIME_PATTERN_WITH_OFFSET.matcher(clientTime).matches()) {
            return clientTime;
        }

        final ZoneOffset zoneOffset = OffsetDateTime.now().getOffset();
        if (StringUtils.isNotBlank(clientTime) && CLIENT_TIME_PATTERN.matcher(clientTime).matches()) {
            return LocalDateTime.parse(clientTime, TIME_INPUT_FORMATTER)
                    .atZone(zoneOffset)
                    .format(TIME_OUTPUT_FORMATTER);
        }
        return LocalDateTime.now().atZone(zoneOffset).format(TIME_OUTPUT_FORMATTER);
    }

    public static String now() {
        return OffsetDateTime.now().format(TIME_OUTPUT_FORMATTER);
    }

}
