package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Defines the contract for bidrequest.ext.prebid
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestPrebid {

    /**
     * Defines the contract for bidrequest.ext.prebid.aliases
     */
    Map<String, String> aliases;

    /**
     * Defines the contract for bidrequest.ext.prebid.bidadjustmentfactors
     */
    Map<String, BigDecimal> bidadjustmentfactors;

    /**
     * Defines the contract for bidrequest.ext.prebid.targeting
     */
    ExtRequestTargeting targeting;

    /**
     * Defines the contract for bidrequest.ext.prebid.storedrequest
     */
    ExtStoredRequest storedrequest;

    /**
     * Defines the contract for bidrequest.ext.prebid.cache
     */
    ExtRequestPrebidCache cache;
}
