package org.prebid.server.analytics.processor;

import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.model.VideoEvent;

public interface AnalyticsEventProcessor<T> {

    T processAmpEvent(AmpEvent event);

    T processAuctionEvent(AuctionEvent event);

    T processCookieSyncEvent(CookieSyncEvent event);

    T processNotificationEvent(NotificationEvent event);

    T processSetuidEvent(SetuidEvent event);

    T processVideoEvent(VideoEvent event);
}
