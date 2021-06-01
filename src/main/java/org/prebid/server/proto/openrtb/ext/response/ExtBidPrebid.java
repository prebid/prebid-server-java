package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.Video;
import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Defines the contract for bidresponse.seatbid.bid[i].ext.prebid
 */
@Builder(toBuilder = true)
@Value
public class ExtBidPrebid {

    String bidid;

    BidType type;

    Map<String, String> targeting;

    @JsonProperty("targetbiddercode")
    String targetBidderCode;

    ExtResponseCache cache;

    @JsonProperty("storedrequestattributes")
    Video storedRequestAttributes;

    Events events;

    ExtBidPrebidVideo video;
}
