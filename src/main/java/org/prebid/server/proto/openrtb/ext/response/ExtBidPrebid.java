package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.iab.openrtb.request.Video;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Defines the contract for bidresponse.seatbid.bid[i].ext.prebid
 */
@Builder(toBuilder = true)
@Data
public class ExtBidPrebid {

    String bidId;

    BidType type;

    Map<String, String> targeting;

    ExtResponseCache cache;

    @JsonProperty("storedrequestattributes")
    Video storedRequestAttributes;

    Events events;

    ExtBidPrebidVideo video;
}
