package com.iab.openrtb.response;

import lombok.Builder;
import lombok.Value;
import org.prebid.server.proto.openrtb.ext.response.ExtBidResponse;

import java.util.List;

/**
 * This object is the top-level bid response object (i.e., the unnamed outer
 * JSON object). The {@code id} attribute is a reflection of the bid request ID
 * for logging purposes. Similarly, {@code bidid} is an optional response
 * tracking ID for bidders. If specified, it can be included in the subsequent
 * win notice call if the bidder wins. At least one {@code seatbid} object is
 * required, which contains at least one bid for an impression. Other attributes
 * are optional.
 * <p>To express a “no-bid”, the options are to return an empty response with
 * HTTP 204. Alternately if the bidder wishes to convey to the exchange a reason
 * for not bidding, just a {@link BidResponse} object is returned with a reason
 * code in the {@code nbr} attribute.
 */
@Builder(toBuilder = true)
@Value
public class BidResponse {

    /**
     * ID of the bid request to which this is a response.
     * (required)
     */
    String id;

    /**
     * Array of seatbid objects; 1+ required if a bid is to be made.
     */
    List<SeatBid> seatbid;

    /**
     * Bidder generated response ID to assist with logging/tracking.
     */
    String bidid;

    /**
     * Bid currency using ISO-4217 alpha codes.
     */
    String cur;

    /**
     * Optional feature to allow a bidder to set data in the exchange’s cookie.
     * The string must be in base85 cookie safe characters and be in any format.
     * Proper JSON encoding must be used to include “escaped” quotation marks.
     */
    String customdata;

    /**
     * Reason for not bidding. Refer to List 5.24.
     */
    Integer nbr;

    /**
     * Placeholder for bidder-specific extensions to OpenRTB.
     */
    ExtBidResponse ext;
}
