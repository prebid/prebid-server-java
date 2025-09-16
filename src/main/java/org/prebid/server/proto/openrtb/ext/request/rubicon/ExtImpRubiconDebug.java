package org.prebid.server.proto.openrtb.ext.request.rubicon;

import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].ext.prebid.bidder.rubicon.debug
 */
@Value(staticConstructor = "of")
public class ExtImpRubiconDebug {

    /**
     * This should be used only for testing.
     * <p>
     * CPM for bid will be replaced with this value.
     */
    Float cpmoverride;
}
