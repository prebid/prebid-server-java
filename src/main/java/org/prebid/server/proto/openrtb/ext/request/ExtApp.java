package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
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

    /**
     * Defines the contract for bidrequest.app.ext.data.
     */
    ObjectNode data;
}
