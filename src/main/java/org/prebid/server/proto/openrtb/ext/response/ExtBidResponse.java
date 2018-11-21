package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Defines the contract for bidresponse.ext
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtBidResponse {

    ExtResponseDebug debug;

    /**
     * Defines the contract for bidresponse.ext.errors
     */
    Map<String, List<ExtBidderError>> errors;

    /**
     * Defines the contract for bidresponse.ext.responsetimemillis
     */
    Map<String, Integer> responsetimemillis;

    /**
     * Defines the contract for bidresponse.ext.usersync
     */
    Map<String, ExtResponseSyncData> usersync;
}
