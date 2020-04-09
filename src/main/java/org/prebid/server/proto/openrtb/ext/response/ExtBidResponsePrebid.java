package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

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

}
