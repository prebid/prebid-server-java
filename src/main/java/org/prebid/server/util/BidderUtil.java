package org.prebid.server.util;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.model.Price;

import java.math.BigDecimal;

public class BidderUtil {

    private BidderUtil() {
    }

    public static boolean isValidPrice(BigDecimal price) {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isValidPrice(Price price) {
        return isValidPrice(price.getValue()) && StringUtils.isNotBlank(price.getCurrency());
    }

    public static boolean isNotEqualsIgnoreCase(String impBidFloorCur, String bidderCurrency) {
        return !StringUtils.equalsIgnoreCase(impBidFloorCur, bidderCurrency);
    }

    public static boolean shouldConvertBidFloor(Price price, String bidderCurrency) {
        return isValidPrice(price)
                && !StringUtils.equals(price.getCurrency(), bidderCurrency);
    }
}
