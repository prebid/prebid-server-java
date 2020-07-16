package org.prebid.server.auction;

import com.iab.openrtb.response.Bid;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.prebid.server.auction.model.AuctionParticipation;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.validation.ResponseBidValidator;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class BidRequester {

    private final long expectedCacheTime;
    private final BidderCatalog bidderCatalog;
    private final HttpBidderRequester httpBidderRequester;
    private final ResponseBidValidator responseBidValidator;
    private final CurrencyConversionService currencyService;
    private final Clock clock;

    public BidRequester(long expectedCacheTime,
                        BidderCatalog bidderCatalog,
                        HttpBidderRequester httpBidderRequester,
                        ResponseBidValidator responseBidValidator,
                        CurrencyConversionService currencyService,
                        Clock clock) {

        if (expectedCacheTime < 0) {
            throw new IllegalArgumentException("Expected cache time should be positive");
        }
        this.expectedCacheTime = expectedCacheTime;
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.httpBidderRequester = Objects.requireNonNull(httpBidderRequester);
        this.responseBidValidator = Objects.requireNonNull(responseBidValidator);
        this.currencyService = Objects.requireNonNull(currencyService);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Passes the request to a corresponding bidder and wraps response in {@link BidderResponse} which also holds
     * recorded response time. Then insert {@link AuctionParticipation}.
     */
    public Future<List<AuctionParticipation>> waitForBidResponses(
            List<AuctionParticipation> auctionParticipations,
            Timeout timeout,
            boolean doCaching,
            boolean debugEnabled,
            BidderAliases aliases,
            Map<String, BigDecimal> bidAdjustments,
            Map<String, Map<String, BigDecimal>> currencyConversionRates) {
        final List<Future> requestedBids = auctionParticipations.stream()
                .map(bidderRequest -> requestBids(bidderRequest, timeout, doCaching, debugEnabled, aliases,
                        bidAdjustments, currencyConversionRates))
                .collect(Collectors.toList());

        // send all the requests to the bidders and gathers results
        return CompositeFuture.join(requestedBids)
                .map(CompositeFuture::list);
    }

    private Future<AuctionParticipation> requestBids(AuctionParticipation auctionParticipation,
                                                     Timeout timeout,
                                                     boolean doCaching,
                                                     boolean debugEnabled,
                                                     BidderAliases aliases,
                                                     Map<String, BigDecimal> bidAdjustments,
                                                     Map<String, Map<String, BigDecimal>> currencyConversionRates) {
        timeout = auctionTimeout(timeout, doCaching);
        if (auctionParticipation.isRequestBlocked()) {
            return Future.succeededFuture(auctionParticipation.insertBidderResponse(null));
        }

        final BidderRequest bidderRequest = auctionParticipation.getBidderRequest();
        final String bidderName = bidderRequest.getBidder();
        final BigDecimal bidPriceAdjustmentFactor = bidAdjustments.get(bidderName);
        final List<String> cur = bidderRequest.getBidRequest().getCur();
        final String adServerCurrency = cur.get(0);
        final Bidder<?> bidder = bidderCatalog.bidderByName(aliases.resolveBidder(bidderName));
        final long startTime = clock.millis();

        return httpBidderRequester.requestBids(bidder, bidderRequest.getBidRequest(), timeout, debugEnabled)
                .map(bidderSeatBid -> validBidderSeatBid(bidderSeatBid, cur))
                .map(seat -> applyBidPriceChanges(seat, currencyConversionRates, adServerCurrency,
                        bidPriceAdjustmentFactor))
                .map(result -> BidderResponse.of(bidderName, result, responseTime(startTime)))
                .map(auctionParticipation::insertBidderResponse);
    }

    /**
     * Validates bid response from exchange.
     * <p>
     * Removes invalid bids from response and adds corresponding error to {@link BidderSeatBid}.
     * <p>
     * Returns input argument as the result if no errors found or create new {@link BidderSeatBid} otherwise.
     */
    private BidderSeatBid validBidderSeatBid(BidderSeatBid bidderSeatBid, List<String> requestCurrencies) {
        final List<BidderBid> bids = bidderSeatBid.getBids();

        final List<BidderBid> validBids = new ArrayList<>(bids.size());
        final List<BidderError> errors = new ArrayList<>(bidderSeatBid.getErrors());

        if (requestCurrencies.size() > 1) {
            errors.add(BidderError.badInput(
                    String.format("Cur parameter contains more than one currency. %s will be used",
                            requestCurrencies.get(0))));
        }

        for (BidderBid bid : bids) {
            final ValidationResult validationResult = responseBidValidator.validate(bid.getBid());
            if (validationResult.hasErrors()) {
                for (String error : validationResult.getErrors()) {
                    errors.add(BidderError.generic(error));
                }
            } else {
                validBids.add(bid);
            }
        }

        return errors.isEmpty() ? bidderSeatBid : BidderSeatBid.of(validBids, bidderSeatBid.getHttpCalls(), errors);
    }

    /**
     * Performs changes on {@link Bid}s price depends on different between adServerCurrency and bidCurrency,
     * and adjustment factor. Will drop bid if currency conversion is needed but not possible.
     * <p>
     * This method should always be invoked after {@link ExchangeService#validBidderSeatBid(BidderSeatBid, List)}
     * to make sure {@link Bid#getPrice()} is not empty.
     */
    private BidderSeatBid applyBidPriceChanges(BidderSeatBid bidderSeatBid,
                                               Map<String, Map<String, BigDecimal>> requestCurrencyRates,
                                               String adServerCurrency, BigDecimal priceAdjustmentFactor) {
        final List<BidderBid> bidderBids = bidderSeatBid.getBids();
        if (bidderBids.isEmpty()) {
            return bidderSeatBid;
        }

        final List<BidderBid> updatedBidderBids = new ArrayList<>(bidderBids.size());
        final List<BidderError> errors = new ArrayList<>(bidderSeatBid.getErrors());

        for (final BidderBid bidderBid : bidderBids) {
            final Bid bid = bidderBid.getBid();
            final String bidCurrency = bidderBid.getBidCurrency();
            final BigDecimal price = bid.getPrice();
            try {
                final BigDecimal finalPrice =
                        currencyService.convertCurrency(price, requestCurrencyRates, adServerCurrency, bidCurrency);

                final BigDecimal adjustedPrice = priceAdjustmentFactor != null
                        && priceAdjustmentFactor.compareTo(BigDecimal.ONE) != 0
                        ? finalPrice.multiply(priceAdjustmentFactor)
                        : finalPrice;

                if (adjustedPrice.compareTo(price) != 0) {
                    bid.setPrice(adjustedPrice);
                }
                updatedBidderBids.add(bidderBid);
            } catch (PreBidException ex) {
                errors.add(BidderError.generic(
                        String.format("Unable to covert bid currency %s to desired ad server currency %s. %s",
                                bidCurrency, adServerCurrency, ex.getMessage())));
            }
        }

        return BidderSeatBid.of(updatedBidderBids, bidderSeatBid.getHttpCalls(), errors);
    }

    private int responseTime(long startTime) {
        return Math.toIntExact(clock.millis() - startTime);
    }

    /**
     * If we need to cache bids, then it will take some time to call prebid cache.
     * We should reduce the amount of time the bidders have, to compensate.
     */
    private Timeout auctionTimeout(Timeout timeout, boolean shouldCacheBids) {
        // A static timeout here is not ideal. This is a hack because we have some aggressive timelines for OpenRTB
        // support.
        // In reality, the cache response time will probably fluctuate with the traffic over time. Someday, this
        // should be replaced by code which tracks the response time of recent cache calls and adjusts the time
        // dynamically.
        return shouldCacheBids ? timeout.minus(expectedCacheTime) : timeout;
    }
}
