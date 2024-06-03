package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public enum Stage {

    entrypoint,

    @JsonAlias("raw-auction-request")
    raw_auction_request,

    @JsonAlias("processed-auction-request")
    processed_auction_request,

    @JsonAlias("bidder-request")
    bidder_request,

    @JsonAlias("raw-bidder-response")
    raw_bidder_response,

    @JsonAlias("processed-bidder-response")
    processed_bidder_response,

    @JsonAlias("all-processed-bid-responses")
    all_processed_bid_responses,

    @JsonAlias("auction-response")
    auction_response
}
