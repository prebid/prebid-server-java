package org.prebid.server.hooks.execution.model;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionResponseHook;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesHook;
import org.prebid.server.hooks.v1.bidder.BidderRequestHook;
import org.prebid.server.hooks.v1.bidder.ProcessedBidderResponseHook;
import org.prebid.server.hooks.v1.bidder.RawBidderResponseHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;

public interface StageWithHookType<TYPE extends Hook<?, ? extends InvocationContext>> {

    StageWithHookType<EntrypointHook> ENTRYPOINT =
            new StageWithHookTypeImpl<>(Stage.entrypoint, EntrypointHook.class);
    StageWithHookType<RawAuctionRequestHook> RAW_AUCTION_REQUEST =
            new StageWithHookTypeImpl<>(Stage.raw_auction_request, RawAuctionRequestHook.class);
    StageWithHookType<ProcessedAuctionRequestHook> PROCESSED_AUCTION_REQUEST =
            new StageWithHookTypeImpl<>(Stage.processed_auction_request, ProcessedAuctionRequestHook.class);
    StageWithHookType<BidderRequestHook> BIDDER_REQUEST =
            new StageWithHookTypeImpl<>(Stage.bidder_request, BidderRequestHook.class);
    StageWithHookType<RawBidderResponseHook> RAW_BIDDER_RESPONSE =
            new StageWithHookTypeImpl<>(Stage.raw_bidder_response, RawBidderResponseHook.class);
    StageWithHookType<ProcessedBidderResponseHook> PROCESSED_BIDDER_RESPONSE =
            new StageWithHookTypeImpl<>(Stage.processed_bidder_response, ProcessedBidderResponseHook.class);
    StageWithHookType<AllProcessedBidResponsesHook> ALL_PROCESSED_BID_RESPONSES =
            new StageWithHookTypeImpl<>(Stage.all_processed_bid_responses, AllProcessedBidResponsesHook.class);
    StageWithHookType<AuctionResponseHook> AUCTION_RESPONSE =
            new StageWithHookTypeImpl<>(Stage.auction_response, AuctionResponseHook.class);

    Stage stage();

    Class<TYPE> hookType();

    static StageWithHookType<? extends Hook<?, ? extends InvocationContext>> forStage(Stage stage) {
        return switch (stage) {
            case entrypoint -> ENTRYPOINT;
            case raw_auction_request -> RAW_AUCTION_REQUEST;
            case processed_auction_request -> PROCESSED_AUCTION_REQUEST;
            case bidder_request -> BIDDER_REQUEST;
            case raw_bidder_response -> RAW_BIDDER_RESPONSE;
            case all_processed_bid_responses -> ALL_PROCESSED_BID_RESPONSES;
            case processed_bidder_response -> PROCESSED_BIDDER_RESPONSE;
            case auction_response -> AUCTION_RESPONSE;
        };
    }
}
