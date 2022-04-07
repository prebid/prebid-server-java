package org.prebid.server.util;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.Bid;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.bidder.model.PriceFloorInfo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

public class BidderUtil {

    private BidderUtil() {
    }

    public static boolean isValidPrice(BigDecimal price) {
        return price != null && price.compareTo(BigDecimal.ZERO) > 0;
    }

    public static boolean isValidPrice(Price price) {
        return isValidPrice(price.getValue()) && StringUtils.isNotBlank(price.getCurrency());
    }

    public static boolean shouldConvertBidFloor(Price price, String bidderCurrency) {
        return isValidPrice(price)
                && !StringUtils.equals(price.getCurrency(), bidderCurrency);
    }

    public static PriceFloorInfo resolvePriceFloor(Bid bid, BidRequest bidRequest) {
        final String bidImpId = ObjectUtil.getIfNotNull(bid, Bid::getImpid);
        if (StringUtils.isEmpty(bidImpId)) {
            return null;
        }

        final List<Imp> imps = ObjectUtil.getIfNotNull(bidRequest, BidRequest::getImp);
        if (CollectionUtils.isEmpty(imps)) {
            return null;
        }

        return imps.stream()
                .filter(imp -> Objects.equals(bidImpId, imp.getId()))
                .findFirst()
                .map(BidderUtil::createFloorInfo)
                .orElse(null);
    }

    private static PriceFloorInfo createFloorInfo(Imp imp) {
        final BigDecimal floor = imp.getBidfloor();
        final String currency = imp.getBidfloorcur();

        return floor != null || currency != null
                ? PriceFloorInfo.of(floor, currency)
                : null;
    }

    public static BigDecimal roundFloor(BigDecimal floor) {
        if (floor != null) {
            final BigDecimal convertedPriceValue = floor.setScale(4, RoundingMode.HALF_EVEN).stripTrailingZeros();

            return convertedPriceValue.scale() < 0
                    ? convertedPriceValue.setScale(0, RoundingMode.HALF_EVEN)
                    : convertedPriceValue;
        }

        return null;
    }
}
