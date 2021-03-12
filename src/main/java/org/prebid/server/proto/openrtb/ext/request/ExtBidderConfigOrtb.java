package org.prebid.server.proto.openrtb.ext.request;

import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.AllArgsConstructor;
import lombok.Value;

@AllArgsConstructor(staticName = "of")
@Value
public class ExtBidderConfigOrtb {

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.ortb2.site
     */
    ObjectNode site;

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.ortb2.app
     */
    ObjectNode app;

    /**
     * Defines the contract for bidrequest.ext.prebid.bidderconfig.config.ortb2.user
     */
    ObjectNode user;
}
