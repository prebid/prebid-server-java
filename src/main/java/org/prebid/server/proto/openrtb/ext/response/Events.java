package org.prebid.server.proto.openrtb.ext.response;

import lombok.AllArgsConstructor;
import lombok.Value;

/**
 * Defines the contract for bidresponse.seatbid.bid[i].ext.prebid.events
 */
@AllArgsConstructor(staticName = "of")
@Value
public class Events {

    String win;

    String imp;
}
