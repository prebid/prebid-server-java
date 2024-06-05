package org.prebid.server.hooks.execution.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

public enum Stage {

    entrypoint,

    @JsonProperty("raw-auction-request")
    @JsonAlias("raw_auction_request")
    raw_auction_request,

    @JsonProperty("processed-auction-request")
    @JsonAlias("processed_auction_request")
    processed_auction_request,

    @JsonProperty("bidder-request")
    @JsonAlias("bidder_request")
    bidder_request,

    @JsonProperty("raw-bidder-response")
    @JsonAlias("raw_bidder_response")
    raw_bidder_response,

    @JsonProperty("processed-bidder-response")
    @JsonAlias("processed_bidder_response")
    processed_bidder_response,

    @JsonProperty("all-processed-bid-responses")
    @JsonAlias("all_processed_bid_responses")
    all_processed_bid_responses,

    @JsonProperty("auction-response")
    @JsonAlias("auction_response")
    auction_response
}
