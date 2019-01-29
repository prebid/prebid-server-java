package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtUserTpId {

    /**
     * PubCommon ID - an ID is generated on the user’s browser and stored for later use on this publisher’s domain.
     * Unique to each publisher domain.
     */
    String source;

    /**
     * Unified ID - a simple cross-vendor approach – it calls out to a URL that responds with that user’s ID in one or
     * more ID spaces (e.g. TradeDesk). The result is stored in the user’s browser for future requests and is passed to
     * bidder adapters to pass it through to SSPs and DSPs that support the ID scheme.
     */
    String uid;
}
