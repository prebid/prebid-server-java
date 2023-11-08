package org.prebid.server.bidder.huaweiads;

import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class HuaweiUtils {

    private HuaweiUtils() {

    }

    public static Optional<String> getIfNotBlank(String value) {
        return Optional.ofNullable(value).filter(StringUtils::isNotBlank);
    }

    public static boolean isFormatDefined(Integer width, Integer height) {
        return height != null && height != 0 && width != null && width != 0;
    }
}
