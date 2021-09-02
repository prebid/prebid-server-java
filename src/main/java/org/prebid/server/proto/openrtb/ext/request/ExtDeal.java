package org.prebid.server.proto.openrtb.ext.request;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].deals[].ext.line
 */
@AllArgsConstructor(staticName = "of")
@Value
public class ExtDeal {

    ExtDealLine line;
}
