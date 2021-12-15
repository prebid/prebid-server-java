package org.prebid.server.util;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Price;

import java.math.BigDecimal;

public class BidderUtil {

    private BidderUtil() {
    }

    public static boolean isValidPrice(BigDecimal price) {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isValidPrice(Price price) {
        return isValidPrice(price.getPrice()) && StringUtils.isNotBlank(price.getCurrency());
    }
}
