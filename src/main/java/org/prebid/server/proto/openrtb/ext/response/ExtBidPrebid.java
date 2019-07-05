package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

/**
 * Defines the contract for bidresponse.seatbid.bid[i].ext.prebid
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtBidPrebid {

    BidType type;

    Map<String, String> targeting;

    ExtResponseCache cache;

    Events events;
}
