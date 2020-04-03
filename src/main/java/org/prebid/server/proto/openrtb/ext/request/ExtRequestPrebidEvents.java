package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.ext.prebid.events
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestPrebidEvents {

    /**
     * Defines the contract for bidrequest.ext.prebid.events.win
     */
    String win;

    /**
     * Defines the contract for bidrequest.ext.prebid.events.imp
     */
    String imp;
}
