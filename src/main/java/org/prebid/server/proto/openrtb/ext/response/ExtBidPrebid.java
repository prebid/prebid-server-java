package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    @JsonProperty("storedrequestattributes")
    ObjectNode storedRequestAttributes;

    Events events;
}
