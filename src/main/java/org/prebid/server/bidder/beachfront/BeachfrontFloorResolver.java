package org.prebid.server.bidder.beachfront;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import lombok.Value;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.Price;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.util.BidderUtil;

import java.math.BigDecimal;
import java.util.Objects;

public class BeachfrontFloorResolver {

    public static final BigDecimal MIN_BID_FLOOR = BigDecimal.valueOf(0.01);
    public static final String DEFAULT_BID_CURRENCY = "USD";

    private final CurrencyConversionService currencyConversionService;

    public BeachfrontFloorResolver(CurrencyConversionService currencyConversionService) {
        this.currencyConversionService = Objects.requireNonNull(currencyConversionService);
    }

    public BidFloorResult resolveBidFloor(BigDecimal extImpBidFloor, Imp imp, BidRequest bidRequest) {
        final BidFloorResult bidFloorResult = convertBidFloor(
                bidRequest, imp.getBidfloor(), extImpBidFloor, imp.getBidfloorcur());

        return bidFloorResult.isError() || bidFloorResult.isWarning()
                ? bidFloorResult
                : proceedWithHigherFloor(extImpBidFloor, bidFloorResult.getPrice().getValue());
    }

    private BidFloorResult convertBidFloor(BidRequest bidRequest,
                                           BigDecimal impBidFloor,
                                           BigDecimal extImpBidFloor,
                                           String impBidFloorCur) {

        final Price initialBidFloorPrice = Price.of(impBidFloorCur, impBidFloor);
        if (!BidderUtil.shouldConvertBidFloor(initialBidFloorPrice, DEFAULT_BID_CURRENCY)) {
            return BidFloorResult.succeeded(initialBidFloorPrice);
        }

        final BidFloorResult result = convertBidFloor(initialBidFloorPrice, bidRequest);

        return result.isError()
                ? processBidFloorFallback(impBidFloor, extImpBidFloor, impBidFloorCur)
                : result;
    }

    private BidFloorResult convertBidFloor(Price bidFloor, BidRequest bidRequest) {
        try {
            final BigDecimal conversionResult = currencyConversionService.convertCurrency(
                    bidFloor.getValue(), bidRequest, bidFloor.getCurrency(), DEFAULT_BID_CURRENCY);

            return BidFloorResult.succeeded(Price.of(DEFAULT_BID_CURRENCY, conversionResult));
        } catch (PreBidException e) {
            return BidFloorResult.error(BidderError.badInput(e.getMessage()));
        }
    }

    private static BidFloorResult processBidFloorFallback(BigDecimal impBidFloor,
                                                          BigDecimal extImpBidFloor,
                                                          String bidFloorCur) {

        return extImpBidFloor != null && extImpBidFloor.compareTo(MIN_BID_FLOOR) > 0
                ? fallbackWithWarning(impBidFloor, extImpBidFloor, bidFloorCur)
                : currencyConversionError(impBidFloor, bidFloorCur);
    }

    private static BidFloorResult fallbackWithWarning(BigDecimal impBidFloor,
                                                      BigDecimal extImpBidFloor,
                                                      String bidFloorCur) {

        final BidderError fallbackWarning = BidderError.badInput(
                String.format(
                        "The following error was received from the currency converter while attempting "
                                + "to convert the imp.bidfloor value of %s from %s to USD:\n"
                                + "Currency service was unable to convert currency.\n"
                                + "The provided value of "
                                + "imp.ext.beachfront.bidfloor, %s USD is being used as a fallback.",
                        impBidFloor, bidFloorCur, extImpBidFloor));

        return BidFloorResult.fallbackWithWarning(Price.of(DEFAULT_BID_CURRENCY, extImpBidFloor), fallbackWarning);
    }

    private static BidFloorResult currencyConversionError(BigDecimal impBidFloor,
                                                          String bidFloorCur) {

        return BidFloorResult.error(
                BidderError.badInput(
                        String.format(
                                "The following error was received from the currency converter while attempting "
                                        + "to convert the imp.bidfloor value of %s from %s to USD:\n"
                                        + "Currency service was unable to convert currency.\nA value of "
                                        + "imp.ext.beachfront.bidfloor was not provided. The bid is being skipped.",
                                impBidFloor, bidFloorCur)));
    }

    private static BidFloorResult proceedWithHigherFloor(BigDecimal extImpBidFloor, BigDecimal convertedBidFloor) {
        final BigDecimal resolvedExtImpBidFloor = ObjectUtils.defaultIfNull(extImpBidFloor, BigDecimal.ZERO);
        if (convertedBidFloor != null && convertedBidFloor.compareTo(resolvedExtImpBidFloor) >= 0) {
            return BidFloorResult.succeeded(Price.of(DEFAULT_BID_CURRENCY, convertedBidFloor));
        }

        final Price resultingPrice = extImpBidFloor != null && extImpBidFloor.compareTo(MIN_BID_FLOOR) > 0
                ? Price.of(DEFAULT_BID_CURRENCY, resolvedExtImpBidFloor)
                : Price.of("", BigDecimal.ZERO);

        return BidFloorResult.succeeded(resultingPrice);
    }

    @Value(staticConstructor = "of")
    public static class BidFloorResult {

        BidderError warning;

        BidderError error;

        Price price;

        public static BidFloorResult error(BidderError error) {
            return of(null, error, null);
        }

        public static BidFloorResult fallbackWithWarning(Price fallback, BidderError warning) {
            return of(warning, null, fallback);
        }

        public static BidFloorResult succeeded(Price price) {
            return of(null, null, price);
        }

        public boolean isError() {
            return error != null;
        }

        public boolean isWarning() {
            return warning != null;
        }
    }
}
