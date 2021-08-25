package org.prebid.server.proto.openrtb.ext.response;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Defines the contract for bidresponse.ext
 */
@Builder(toBuilder = true)
@Value
public class ExtBidResponse {

    ExtResponseDebug debug;

    /**
     * Defines the contract for bidresponse.ext.errors
     */
    Map<String, List<ExtBidderError>> errors;

    /**
     * Defines the contract for bidresponse.ext.warnings
     */
    Map<String, List<ExtBidderError>> warnings;

    /**
     * Defines the contract for bidresponse.ext.responsetimemillis
     */
    Map<String, Integer> responsetimemillis;
    /** 
    private static BidResponse givenBidResponse(UnaryOperator<Bid.BidBuilder>... bidCustomizers) {
        return BidResponse.builder()
                .cur("USD")
                .seatbid(singletonList(SeatBid.builder()
                        .bid(Arrays.stream(bidCustomizers)
                                .map(bidCustomizer -> bidCustomizer.apply(Bid.builder()).build())
                                .collect(Collectors.toList()))
                        .build()))
                .build();
    }
     * RequestTimeoutMillis returns the timeout used in the auction.
     * This is useful if the timeout is saved in the Stored Request on the server.
     * Clients can run one auction, and then use this to set better connection timeouts on future auction requests.
     */
    Long tmaxrequest;

    /**
     * Defines the contract for bidresponse.ext.usersync
     */
    Map<String, ExtResponseSyncData> usersync;

    /**
     * Defines the contract for bidresponse.ext.prebid
     */
    ExtBidResponsePrebid prebid;
}
