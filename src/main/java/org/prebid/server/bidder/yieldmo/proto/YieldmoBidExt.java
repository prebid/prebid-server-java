package org.prebid.server.bidder.yieldmo.proto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

/**
 * Defines the contract for bidresponse.seatbid[i].bid[i].ext
 */
@Value(staticConstructor = "of")
public class YieldmoBidExt {

    @JsonProperty("mediatype")
    String mediaType;
}
