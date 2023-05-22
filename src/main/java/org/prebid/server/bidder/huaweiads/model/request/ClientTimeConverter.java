package org.prebid.server.bidder.huaweiads.model.request;

import org.prebid.server.bidder.huaweiads.model.util.HuaweiAdsConstants;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

public class ClientTimeConverter {

    private ClientTimeConverter() {

    }

    private static final String TIME_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
    private static final Pattern CLIENT_TIME_PATTERN_1 =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}[+-]{1}\\d{4}$");
    private static final Pattern CLIENT_TIME_PATTERN_2 =
            Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}$");

    public static String getClientTime(String clientTime) {
        String zone = HuaweiAdsConstants.DEFAULT_TIME_ZONE;
        String t = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM yy HH:mm Z"));
        int index = t.contains("+") ? t.indexOf("+") : t.indexOf("-");
        if (index > 0 && t.length() - index == 6) {
            zone = t.substring(index);
        }
        if (clientTime == null || clientTime.isBlank()) {
            return LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT)) + zone;
        }
        if (CLIENT_TIME_PATTERN_1.matcher(clientTime).matches()) {
            return clientTime;
        }
        if (CLIENT_TIME_PATTERN_2.matcher(clientTime).matches()) {
            return clientTime + zone;
        }
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern(TIME_FORMAT)) + zone;
    }
}
