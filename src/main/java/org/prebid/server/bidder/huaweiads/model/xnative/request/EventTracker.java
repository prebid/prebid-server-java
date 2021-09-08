package org.prebid.server.bidder.huaweiads.model.xnative.request;

import lombok.AllArgsConstructor;
import org.prebid.server.proto.openrtb.ext.request.ExtImp;

/**
 * The event trackers object specifies the types of events the bidder can request to be tracked in the bid response, and which types of tracking are available for each event type, and is included as an array in the request.
 */

@AllArgsConstructor
public class EventTracker {
    private int eventType;
    private int eventTrackingMethods;
    private ExtImp rawMessage;
}
