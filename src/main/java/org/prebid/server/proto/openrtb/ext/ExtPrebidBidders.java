package org.prebid.server.proto.openrtb.ext;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Value;
import org.prebid.server.bidder.Bidder;

/**
 * Can be used by {@link Bidder}s to unmarshal any request.ext.prebid.bidders.
 */
@Value(staticConstructor = "of")
public class ExtPrebidBidders {

    /**
     * Defines the contract for request.ext.prebid.bidders.bidder
     */
    JsonNode bidder;
}
