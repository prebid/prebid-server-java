package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

@Value(staticConstructor = "of")
public class ExtBidderConfig {

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.ortb2
     */
    ExtBidderConfigOrtb ortb2;
}
