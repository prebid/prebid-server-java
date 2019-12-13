package com.iab.openrtb.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * ExtBidPrebidVideo defines the contract for bidresponse.seatbid.bid[i].ext.prebid.video
 */
@AllArgsConstructor(staticName = "of")
@Data
public class BidVideo {

    Integer duration;

    String primaryCategory;
}
