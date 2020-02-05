package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;
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
    Integer debug;

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

    /**
     * Defines the contract for bidrequest.ext.prebid.schains
     */
    List<ExtRequestPrebidSchain> schains;

    /**
     * Defines the contract for bidrequest.ext.prebid.bidders
     */
    ObjectNode bidders;
}

