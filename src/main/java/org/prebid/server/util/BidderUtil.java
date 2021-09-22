package org.prebid.server.util;

import java.math.BigDecimal;

public class BidderUtil {

    private BidderUtil() {
    }

    public static boolean isValidPrice(BigDecimal price) {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }
}
