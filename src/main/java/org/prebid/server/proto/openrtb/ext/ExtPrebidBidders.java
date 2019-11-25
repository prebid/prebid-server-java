package org.prebid.server.proto.openrtb.ext;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.bidder.Bidder;

/**
 * Can be used by {@link Bidder}s to unmarshal any request.ext.prebid.bidders.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtPrebidBidders {

    /**
     * Defines the contract for request.ext.prebid.bidders.bidder
     */
    JsonNode bidder;
}

