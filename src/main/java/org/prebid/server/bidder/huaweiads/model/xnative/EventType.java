package org.prebid.server.bidder.huaweiads.model.xnative;

public class EventType {
    private static final int eventTypeImpression = 1; // Impression
    private static final int eventTypeViewableMRC50 = 2; // Visible impression using MRC definition at 50% in view for 1 second
    private static final int eventTypeViewableMRC100 = 3; // 100% in view for 1 second (ie GroupM standard)
    private static final int eventTypeViewableVideo50 = 4; // Visible impression for video using MRC definition at 50% in view for 2 seconds
}
