package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidresponse.seatbid.bid[i].ext.prebid.video
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtBidPrebidVideo {

    Integer duration;

    String primaryCategory;
}
