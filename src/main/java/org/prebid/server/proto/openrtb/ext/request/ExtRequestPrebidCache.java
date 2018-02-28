package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.cache
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestPrebidCache {

    /**
     * Defines the contract for bidrequest.ext.prebid.cache.bids
     */
    JsonNode bids;
}
