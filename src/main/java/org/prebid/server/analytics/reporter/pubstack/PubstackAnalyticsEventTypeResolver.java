package org.prebid.server.analytics.reporter.pubstack;

import org.prebid.server.analytics.AnalyticsEventProcessor;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.analytics.reporter.pubstack.model.EventType;

public class PubstackAnalyticsEventTypeResolver implements AnalyticsEventProcessor<EventType> {

    @Override
    public EventType processAmpEvent(AmpEvent event) {
        return EventType.amp;
    }

    @Override
    public EventType processAuctionEvent(AuctionEvent event) {
        return EventType.auction;
    }

    @Override
    public EventType processCookieSyncEvent(CookieSyncEvent event) {
        return EventType.cookiesync;
    }

    @Override
    public EventType processNotificationEvent(NotificationEvent event) {
        return EventType.notification;
    }

    @Override
    public EventType processSetuidEvent(SetuidEvent event) {
        return EventType.setuid;
    }

    @Override
    public EventType processVideoEvent(VideoEvent event) {
        return EventType.video;
    }
}
