package org.prebid.server.proto.openrtb.ext;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.bidder.Bidder;

/**
 * Can be used by {@link Bidder}s to unmarshal any request.ext.prebid.bidders.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtPrebidBidders<B> {

    /**
     * Contains the bidder-specific extension.
     * <p>
     * Each bidder should specify their corresponding ExtRequestPrebid{Bidder} class as a type argument when
     * unmarshaling extension using this class.
     * <p>
     */
    B bidder;
}
