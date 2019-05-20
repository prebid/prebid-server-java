package org.prebid.server.proto.openrtb.ext.request;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Defines the contract for bidrequest.ext.prebid
 */
@Builder(toBuilder = true)
@Value
public class ExtRequestPrebid {

    /**
     * Defines the contract for bidrequest.ext.prebid.debug
     */
    Boolean debug;

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

    /**
     * Defines the contract for bidrequest.ext.prebid.data
     */
    ExtRequestPrebidData data;
}
