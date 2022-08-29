package org.prebid.server.proto.openrtb.ext.request;

import com.iab.openrtb.request.SupplyChain;
import lombok.Value;

import java.util.List;

/**
 * Defines the contract for bidrequest.ext.prebid.schains
 */
@Value(staticConstructor = "of")
public class ExtRequestPrebidSchain {

    /**
     * Defines the contract for bidrequest.ext.prebid.schains[i].bidders
     */
    List<String> bidders;

    /**
     * Defines the contract for bidrequest.ext.prebid.schains[i].schain
     */
    SupplyChain schain;
}

