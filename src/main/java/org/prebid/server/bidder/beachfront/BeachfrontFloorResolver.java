package org.prebid.server.bidder.beachfront;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import lombok.Value;
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

    public BidFloorResult resolveBidFloor(BigDecimal extImpBidfloor, Imp imp, BidRequest bidRequest) {
        final BigDecimal resolvedImpBidFloor = zeroIfNull(imp.getBidfloor());
        final BigDecimal resolvedExtImpBidFloor = zeroIfNull(extImpBidfloor);

        final BidFloorResult bidFloorResult = convertBidFloor(
                bidRequest, resolvedImpBidFloor, resolvedExtImpBidFloor, imp.getBidfloorcur());

        return bidFloorResult.isError()
                ? bidFloorResult
                : postProcessBidFloor(resolvedExtImpBidFloor, bidFloorResult.getPrice().getValue());
    }

    private static BigDecimal zeroIfNull(BigDecimal bigDecimal) {
        return bigDecimal == null ? BigDecimal.ZERO : bigDecimal;
    }

    private BidFloorResult convertBidFloor(BidRequest bidRequest,
                                           BigDecimal impBidFloor,
                                           BigDecimal extImpBidFloor,
                                           String impBidFloorCur) {

        if (!BidderUtil.shouldConvertBidFloor(Price.of(impBidFloorCur, impBidFloor), DEFAULT_BID_CURRENCY)) {
            return BidFloorResult.succeeded(Price.of(impBidFloorCur, impBidFloor));
        }

        final BidFloorResult result = convertBidFloor(impBidFloorCur, impBidFloor, bidRequest);

        return result.isError()
                ? processBidFloorFallback(impBidFloor, extImpBidFloor, impBidFloorCur)
                : result;
    }

    private BidFloorResult convertBidFloor(String bidFloorCur, BigDecimal bidFloor, BidRequest bidRequest) {
        try {
            final BigDecimal convertationResult = currencyConversionService.convertCurrency(
                    bidFloor, bidRequest, bidFloorCur, DEFAULT_BID_CURRENCY);
            return BidFloorResult.succeeded(Price.of(DEFAULT_BID_CURRENCY, convertationResult));
        } catch (PreBidException e) {
            return BidFloorResult.error(BidderError.badInput(e.getMessage()));
        }
    }

    private static BidFloorResult processBidFloorFallback(BigDecimal impBidFloor,
                                                          BigDecimal extImpBidFloor,
                                                          String bidFloorCur) {

        final String currencyConversionErrorMessage = "Currency service was unable to convert currency.";

        if (extImpBidFloor.compareTo(MIN_BID_FLOOR) > 0) {
            final String errorMessage = String.format(
                    "The following error was received from the currency converter while attempting "
                            + "to convert the imp.bidfloor value of %s from %s to USD:\n%s\n"
                            + "The provided value of "
                            + "imp.ext.beachfront.bidfloor, %s USD is being used as a fallback.",
                    impBidFloor,
                    bidFloorCur,
                    currencyConversionErrorMessage,
                    extImpBidFloor);
            return BidFloorResult.fallback(
                    Price.of(DEFAULT_BID_CURRENCY, extImpBidFloor),
                    BidderError.badInput(errorMessage));
        } else {
            final String errorMessage = String.format(
                    "The following error was received from the currency converter while attempting "
                            + "to convert the imp.bidfloor value of %s from %s to USD:\n%s\nA value of "
                            + "imp.ext.beachfront.bidfloor was not provided. The bid is being skipped.",
                    impBidFloor,
                    bidFloorCur,
                    currencyConversionErrorMessage);
            return BidFloorResult.fatalError(BidderError.badInput(errorMessage));
        }
    }

    private static BidFloorResult postProcessBidFloor(BigDecimal extImpBidFloor,
                                                      BigDecimal convertedBidFloor) {

        if (convertedBidFloor.compareTo(extImpBidFloor) < 0) {
            final Price resultingPrice = extImpBidFloor.compareTo(MIN_BID_FLOOR) > 0
                    ? Price.of(DEFAULT_BID_CURRENCY, extImpBidFloor)
                    : Price.of("", BigDecimal.ZERO);

            return BidFloorResult.succeeded(resultingPrice);
        }

        return BidFloorResult.succeeded(Price.of(DEFAULT_BID_CURRENCY, convertedBidFloor));
    }

    @Value(staticConstructor = "of")
    public static class BidFloorResult {

        boolean fatal;

        BidderError error;

        Price price;

        public static BidFloorResult fatalError(BidderError error) {
            return of(true, error, null);
        }

        public static BidFloorResult error(BidderError error) {
            return of(false, error, null);
        }

        public static BidFloorResult fallback(Price fallback, BidderError error) {
            return of(false, error, fallback);
        }

        public static BidFloorResult succeeded(Price price) {
            return of(false, null, price);
        }

        public boolean isError() {
            return error != null;
        }
    }
}
