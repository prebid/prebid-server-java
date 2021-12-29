package org.prebid.server.analytics.processor;

import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.metric.MetricName;

public class MetricsEventTypeAnalyticsEventProcessor implements AnalyticsEventProcessor<MetricName> {

    @Override
    public MetricName processAmpEvent(AmpEvent event) {
        return MetricName.event_amp;
    }

    @Override
    public MetricName processAuctionEvent(AuctionEvent event) {
        return MetricName.event_auction;
    }

    @Override
    public MetricName processCookieSyncEvent(CookieSyncEvent event) {
        return MetricName.event_cookie_sync;
    }

    @Override
    public MetricName processNotificationEvent(NotificationEvent event) {
        return MetricName.event_notification;
    }

    @Override
    public MetricName processSetuidEvent(SetuidEvent event) {
        return MetricName.event_setuid;
    }

    @Override
    public MetricName processVideoEvent(VideoEvent event) {
        return MetricName.event_video;
    }
}
