package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.imp[i].ext.prebid
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtImpPrebid {

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.storedrequest
     */
    ExtStoredRequest storedrequest;

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.storedAuctionResponse
     */
    ExtStoredAuctionResponse storedAuctionResponse;

    /**
     * Defines the contract for bidrequest.imp[i].ext.prebid.storedBidResponse
     */
    List<ExtStoredSeatBid> storedBidResponse;
}
