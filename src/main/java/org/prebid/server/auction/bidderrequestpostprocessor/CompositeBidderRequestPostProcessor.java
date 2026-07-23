package org.prebid.server.auction.bidderrequestpostprocessor;

import io.vertx.core.Future;
import org.prebid.server.auction.aliases.BidderAliases;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.util.ListUtil;

import java.util.List;
import java.util.Objects;

public class CompositeBidderRequestPostProcessor implements BidderRequestPostProcessor {

    private final List<BidderRequestPostProcessor> bidderRequestPostProcessors;

    public CompositeBidderRequestPostProcessor(List<BidderRequestPostProcessor> bidderRequestPostProcessors) {
        this.bidderRequestPostProcessors = Objects.requireNonNull(bidderRequestPostProcessors);
    }

    @Override
    public Future<BidderRequestPostProcessingResult> process(BidderRequest bidderRequest,
                                                             BidderAliases aliases,
                                                             AuctionContext auctionContext) {

        Future<BidderRequestPostProcessingResult> result = initialResult(bidderRequest);
        for (BidderRequestPostProcessor bidderRequestPostProcessor : bidderRequestPostProcessors) {
            result = result.compose(previous ->
                    bidderRequestPostProcessor.process(previous.getValue(), aliases, auctionContext)
                            .map(current -> mergeErrors(previous, current)));
        }

        return result;
    }

    private static Future<BidderRequestPostProcessingResult> initialResult(BidderRequest bidderRequest) {
        return Future.succeededFuture(BidderRequestPostProcessingResult.withValue(bidderRequest));
    }

    private static BidderRequestPostProcessingResult mergeErrors(BidderRequestPostProcessingResult previous,
                                                                 BidderRequestPostProcessingResult current) {

        return BidderRequestPostProcessingResult.of(
                current.getValue(),
                ListUtil.union(previous.getErrors(), current.getErrors()));
    }
}
