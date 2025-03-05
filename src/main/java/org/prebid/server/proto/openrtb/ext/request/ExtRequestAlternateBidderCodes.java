package org.prebid.server.proto.openrtb.ext.request;

import lombok.Builder;
import lombok.Value;

import java.util.Map;


/**
 * Defines the contract for bidrequest.ext.prebid.alternatebiddercodes
 */
@Builder(toBuilder = true)
@Value
public class ExtRequestAlternateBidderCodes {
    /**
     * Is this feature enabled
     */
    Boolean enabled;

    Map<String, ExtRequestAlternateBidderCodesBidder> bidders;
}
