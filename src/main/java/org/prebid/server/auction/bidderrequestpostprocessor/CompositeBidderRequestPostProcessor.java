package org.prebid.server.auction.bidderrequestpostprocessor;

import io.vertx.core.Future;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.util.ListUtil;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CompositeBidderRequestPostProcessor implements BidderRequestPostProcessor {

    private final List<BidderRequestPostProcessor> bidderRequestPostProcessors;

    public CompositeBidderRequestPostProcessor(List<BidderRequestPostProcessor> bidderRequestPostProcessors) {
        this.bidderRequestPostProcessors = Objects.requireNonNull(bidderRequestPostProcessors);
    }

    @Override
    public Future<Result<BidderRequest>> process(BidderRequest bidderRequest,
                                                 BidderAliases aliases,
                                                 AuctionContext auctionContext) {

        Future<Result<BidderRequest>> result = initialResult(bidderRequest);
        for (BidderRequestPostProcessor bidderRequestPostProcessor : bidderRequestPostProcessors) {
            result = result.compose(previous ->
                    bidderRequestPostProcessor.process(previous.getValue(), aliases, auctionContext)
                            .map(current -> mergeErrors(previous, current)));
        }

        return result;
    }

    private static Future<Result<BidderRequest>> initialResult(BidderRequest bidderRequest) {
        return Future.succeededFuture(Result.of(bidderRequest, Collections.emptyList()));
    }

    private static Result<BidderRequest> mergeErrors(Result<BidderRequest> previous, Result<BidderRequest> current) {
        return Result.of(current.getValue(), ListUtil.union(previous.getErrors(), current.getErrors()));
    }
}
