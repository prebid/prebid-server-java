package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.ext.prebid.targeting
 */
@Value(staticConstructor = "of")
public class ExtRequestPrebidMultiBid {

    /**
     * Defines the contract for bidrequest.ext.prebid.multibid.bidder
     */
    String bidder;

    /**
     * Defines the contract for bidrequest.ext.prebid.multibid.bidders
     */
    List<String> bidders;

    /**
     * Defines the contract for bidrequest.ext.prebid.multibid.maxbids
     */
    @JsonProperty("maxbids")
    Integer maxBids;

    /**
     * Defines the contract for bidrequest.ext.prebid.multibid.targetbiddercodeprefix
     */
    @JsonProperty("targetbiddercodeprefix")
    String targetBidderCodePrefix;
}
