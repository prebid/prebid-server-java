package org.prebid.server.hooks.execution.model;

public enum Stage {

    entrypoint,
    raw_auction_request,
    processed_auction_request,
    bidder_request,
    raw_bidder_response,
    processed_bidder_response,
    auction_response
}
