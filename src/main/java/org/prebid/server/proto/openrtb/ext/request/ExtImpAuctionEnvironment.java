package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonFormat;

/**
 * AuctionEnvironment is a Google Privacy Sandbox flag indicating where the auction may take place
 */
@JsonFormat(shape = JsonFormat.Shape.NUMBER_INT)
public enum ExtImpAuctionEnvironment {

    /**
     * 0 Standard server-side auction
     */
    SERVER_SIDE_AUCTION,

    /**
     * 1 On-device interest group auction (FLEDGE)
     */
    ON_DEVICE_IG_AUCTION_FLEDGE,

    /**
     * 2 Server-side with interest group simulation
     */
    SERVER_SIDE_WITH_IG_SIMULATION
}
