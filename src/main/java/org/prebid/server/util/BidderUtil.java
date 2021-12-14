package org.prebid.server.util;

import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Price;

import java.math.BigDecimal;

public class BidderUtil {

    private BidderUtil() {
    }

    public static boolean isValidPrice(Price price) {
        final BigDecimal value = price.getPrice();

        return value != null
                && value.compareTo(BigDecimal.ZERO) > 0
                && StringUtils.isNotBlank(price.getCurrency());
    }
}
