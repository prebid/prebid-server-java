package org.prebid.server.proto.openrtb.ext;

import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.bidder.Bidder;

/**
 * Can be used by {@link Bidder}s to unmarshal any request.ext.prebid.
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtReqPrebid<B> {

    ExtPrebidBidders<B> bidders;
}
