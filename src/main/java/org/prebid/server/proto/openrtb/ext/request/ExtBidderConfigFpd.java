package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtBidderConfigFpd {

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.fpd.site
     */
    ObjectNode site;

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.fpd.app
     */
    ObjectNode app;

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.fpd.user
     */
    ObjectNode user;
}
