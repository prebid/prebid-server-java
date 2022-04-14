package org.prebid.server.proto.openrtb.ext.response;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.Map;

/**
 * Defines the contract for bidresponse.ext
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtBidResponsePrebid {

    /**
     * Defines the contract for bidresponse.ext.prebid.auctiontimstamp
     */
    Long auctiontimestamp;

    /**
     * Defines the contract for bidresponse.ext.prebid.modules
     */
    ExtModules modules;

    /**
     * Additional targeting key/values for the bid response (only used for AMP)
     * Set targeting options here that will occur in the bidResponse no matter if
     * a bid won the auction or not.
     */
    Map<String, JsonNode> targeting;
}
