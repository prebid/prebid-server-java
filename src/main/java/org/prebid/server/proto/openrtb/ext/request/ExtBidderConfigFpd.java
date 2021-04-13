package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtBidderConfigFpd {

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.fpd.context
     */
    JsonNode context;

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.fpd.user
     */
    JsonNode user;
}
