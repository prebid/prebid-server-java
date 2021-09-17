package org.prebid.server.proto.openrtb.ext.response;

import com.iab.openrtb.request.BidRequest;
import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Defines the contract for bidresponse.ext.debug
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtResponseDebug {

    /**
     * Defines the contract for bidresponse.ext.debug.httpcalls
     */
    Map<String, List<ExtHttpCall>> httpcalls;

    /**
     * Request after resolution of stored requests and debug overrides
     */
    BidRequest resolvedrequest;

    /**
     * Defines the contract for bidresponse.ext.debug.pgmetrics
     */
    ExtDebugPgmetrics pgmetrics;

    /**
     * Defines the contract for bidresponse.ext.debug.trace
     */
    ExtDebugTrace trace;
}
