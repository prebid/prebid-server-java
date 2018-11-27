package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.app.ext
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtApp {

    /**
     * Defines the contract for bidrequest.ext.app.prebid
     */
    ExtAppPrebid prebid;
}
