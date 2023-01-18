package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtBidderConfig {

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.fpd
     */
    ExtBidderConfigFpd fpd;

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.ortb2
     */
    ExtBidderConfigOrtb ortb2;
}
