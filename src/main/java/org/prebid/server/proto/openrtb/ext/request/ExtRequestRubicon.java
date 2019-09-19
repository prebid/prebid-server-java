package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.rubicon
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestRubicon {

    /**
     * Defines the contract for bidrequest.ext.rubicon.debug
     */
    ExtRequestRubiconDebug debug;
}
