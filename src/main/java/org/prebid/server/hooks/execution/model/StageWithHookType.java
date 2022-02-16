package org.prebid.server.hooks.execution.model;

import org.prebid.server.hooks.v1.Hook;
import org.prebid.server.hooks.v1.InvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionResponseHook;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.hooks.v1.auction.RawAuctionRequestHook;
import org.prebid.server.hooks.v1.bidder.BidderRequestHook;
import org.prebid.server.hooks.v1.bidder.ProcessedBidderResponseHook;
import org.prebid.server.hooks.v1.bidder.RawBidderResponseHook;
import org.prebid.server.hooks.v1.entrypoint.EntrypointHook;

public interface StageWithHookType<TYPE extends Hook<?, ? extends InvocationContext>> {

    StageWithHookType<EntrypointHook> ENTRYPOINT =
            new StageWithHookTypeImpl<>(Stage.ENTRYPOINT, EntrypointHook.class);
    StageWithHookType<RawAuctionRequestHook> RAW_AUCTION_REQUEST =
            new StageWithHookTypeImpl<>(Stage.RAW_AUCTION_REQUEST, RawAuctionRequestHook.class);
    StageWithHookType<ProcessedAuctionRequestHook> PROCESSED_AUCTION_REQUEST =
            new StageWithHookTypeImpl<>(Stage.PROCESSED_AUCTION_REQUEST, ProcessedAuctionRequestHook.class);
    StageWithHookType<BidderRequestHook> BIDDER_REQUEST =
            new StageWithHookTypeImpl<>(Stage.BIDDER_REQUEST, BidderRequestHook.class);
    StageWithHookType<RawBidderResponseHook> RAW_BIDDER_RESPONSE =
            new StageWithHookTypeImpl<>(Stage.RAW_BIDDER_RESPONSE, RawBidderResponseHook.class);
    StageWithHookType<ProcessedBidderResponseHook> PROCESSED_BIDDER_RESPONSE =
            new StageWithHookTypeImpl<>(Stage.PROCESSED_BIDDER_RESPONSE, ProcessedBidderResponseHook.class);
    StageWithHookType<AuctionResponseHook> AUCTION_RESPONSE =
            new StageWithHookTypeImpl<>(Stage.AUCTION_RESPONSE, AuctionResponseHook.class);

    Stage stage();

    Class<TYPE> hookType();

    static StageWithHookType<? extends Hook<?, ? extends InvocationContext>> forStage(Stage stage) {
        switch (stage) {
            case ENTRYPOINT:
                return ENTRYPOINT;
            case RAW_AUCTION_REQUEST:
                return RAW_AUCTION_REQUEST;
            case PROCESSED_AUCTION_REQUEST:
                return PROCESSED_AUCTION_REQUEST;
            case BIDDER_REQUEST:
                return BIDDER_REQUEST;
            case RAW_BIDDER_RESPONSE:
                return RAW_BIDDER_RESPONSE;
            case PROCESSED_BIDDER_RESPONSE:
                return PROCESSED_BIDDER_RESPONSE;
            case AUCTION_RESPONSE:
                return AUCTION_RESPONSE;
            default:
                throw new IllegalStateException(String.format("Unknown stage %s", stage));
        }
    }
}
