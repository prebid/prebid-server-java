package org.prebid.server.proto.openrtb.ext.request.magnite;

import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.prebid.bidder.magnite.debug
 */
@Value(staticConstructor = "of")
public class ExtImpMagniteDebug {

    /**
     * This should be used only for testing.
     * <p>
     * CPM for bid will be replaced with this value.
     */
    Float cpmoverride;
}
