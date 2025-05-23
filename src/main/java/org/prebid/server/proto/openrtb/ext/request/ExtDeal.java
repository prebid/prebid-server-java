package org.prebid.server.proto.openrtb.ext.request;

import lombok.Value;

/**
 * Defines the contract for bidrequest.imp[i].deals[].ext.line
 */
@Value(staticConstructor = "of")
public class ExtDeal {

    ExtDealLine line;
}
