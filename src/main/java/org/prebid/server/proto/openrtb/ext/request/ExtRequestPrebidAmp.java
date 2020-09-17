package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

import java.util.Map;

/**
 * Defines the contract for ext.prebid.amp
 */
@Value(staticConstructor = "of")
public class ExtRequestPrebidAmp {

    /**
     * Defines the contract for ext.prebid.amp.data
     */
    Map<String, String> data;
}

