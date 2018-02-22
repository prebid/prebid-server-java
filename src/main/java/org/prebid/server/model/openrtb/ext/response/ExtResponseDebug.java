package org.prebid.server.model.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Defines the contract for bidresponse.ext.debug
 */
@AllArgsConstructor(staticName = "of")
@Value
public final class ExtResponseDebug {

    /**
     * Defines the contract for bidresponse.ext.debug.httpcalls
     */
    Map<String, List<ExtHttpCall>> httpcalls;
}
