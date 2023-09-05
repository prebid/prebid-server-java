package org.prebid.server.bidder.huaweiads.model.response;

import org.apache.commons.lang3.StringUtils;

class AdmUtils {

    private AdmUtils() {

    }

    public static <T> String getOrEmpty(T value) {
        return value == null ? StringUtils.EMPTY : value.toString();
    }
}
