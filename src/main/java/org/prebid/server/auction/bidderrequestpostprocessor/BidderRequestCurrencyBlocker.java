package org.prebid.server.auction.bidderrequestpostprocessor;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderInfo;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.Result;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class BidderRequestCurrencyBlocker implements BidderRequestPostProcessor {

    private final BidderCatalog bidderCatalog;

    public BidderRequestCurrencyBlocker(BidderCatalog bidderCatalog) {
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    @Override
    public Future<Result<BidderRequest>> process(BidderRequest bidderRequest,
                                                 BidderAliases aliases,
                                                 AuctionContext auctionContext) {

        if (isAcceptableCurrency(bidderRequest.getBidRequest(), aliases.resolveBidder(bidderRequest.getBidder()))) {
            return Future.succeededFuture(Result.of(bidderRequest, Collections.emptyList()));
        }

        return Future.failedFuture(new BidderRequestRejectedException(
                BidRejectionReason.REQUEST_BLOCKED_UNACCEPTABLE_CURRENCY,
                Collections.singletonList(
                        BidderError.generic("No match between the configured currencies and bidRequest.cur"))));
    }

    private boolean isAcceptableCurrency(BidRequest bidRequest, String originalBidderName) {
        final List<String> requestCurrencies = bidRequest.getCur();
        final Set<String> bidAcceptableCurrencies =
                Optional.ofNullable(bidderCatalog.bidderInfoByName(originalBidderName))
                        .map(BidderInfo::getCurrencyAccepted)
                        .orElse(null);

        return CollectionUtils.isEmpty(requestCurrencies)
                || CollectionUtils.isEmpty(bidAcceptableCurrencies)
                || requestCurrencies.stream().anyMatch(bidAcceptableCurrencies::contains);
    }
}
