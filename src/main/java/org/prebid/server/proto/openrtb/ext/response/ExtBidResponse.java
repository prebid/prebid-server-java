package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Defines the contract for bidresponse.ext
 */
@Builder(toBuilder = true)
@Value
public class ExtBidResponse {

    ExtResponseDebug debug;

    /**
     * Defines the contract for bidresponse.ext.errors
     */
    Map<String, List<ExtBidderError>> errors;

    /**
     * Defines the contract for bidresponse.ext.warnings
     */
    Map<String, List<ExtBidderError>> warnings;

    /**
     * Defines the contract for bidresponse.ext.responsetimemillis
     */
    Map<String, Integer> responsetimemillis;

    /**
     * RequestTimeoutMillis returns the timeout used in the auction.
     * This is useful if the timeout is saved in the Stored Request on the server.
     * Clients can run one auction, and then use this to set better connection timeouts on future auction requests.
     */
    Long tmaxrequest;

    /**
     * Defines the contract for bidresponse.ext.usersync
     */
    Map<String, ExtResponseSyncData> usersync;

    /**
     * Defines the contract for bidresponse.ext.prebid
     */
    ExtBidResponsePrebid prebid;

    /**
     * Additional targeting key/values for the bid response.
     * Set targeting options here that will occur in the bidResponse no matter if
     * a bid won the auction or not.
     */
    Map<String, JsonNode> additionalTargeting;
}
