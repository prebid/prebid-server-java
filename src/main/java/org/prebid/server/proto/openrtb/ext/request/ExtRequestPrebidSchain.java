package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.ext.prebid.schains
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtRequestPrebidSchain {

    /**
     * Defines the contract for bidrequest.ext.prebid.schains[i].bidders
     */
    List<String> bidders;

    /**
     * Defines the contract for bidrequest.ext.prebid.schains[i].schain
     */
    ExtSourceSchain schain;
}

