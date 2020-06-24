package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.source.ext
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtSource {

    /**
     * Defines the contract for bidrequest.source.ext.schain
     */
    ExtRequestPrebidSchainSchain schain;
}

