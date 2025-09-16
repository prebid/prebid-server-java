package org.prebid.server.proto.openrtb.ext.response;

import lombok.Value;

/**
 * Defines the contract for bidresponse.seatbid.bid[i].ext.prebid.events
 */
@Value(staticConstructor = "of")
public class Events {

    String win;

    String imp;
}
