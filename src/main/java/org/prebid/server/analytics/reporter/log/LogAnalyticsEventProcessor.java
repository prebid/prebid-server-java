package org.prebid.server.analytics.reporter.log;

import org.prebid.server.analytics.AnalyticsEventProcessor;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.analytics.reporter.log.model.LogEvent;

public class LogAnalyticsEventProcessor implements AnalyticsEventProcessor<LogEvent<?>> {

    @Override
    public LogEvent<?> processAmpEvent(AmpEvent event) {
        return LogEvent.of("/openrtb2/amp", event.getBidResponse());
    }

    @Override
    public LogEvent<?> processAuctionEvent(AuctionEvent event) {
        return LogEvent.of("/openrtb2/auction", event.getBidResponse());
    }

    @Override
    public LogEvent<?> processCookieSyncEvent(CookieSyncEvent event) {
        return LogEvent.of("/cookie_sync", event.getBidderStatus());
    }

    @Override
    public LogEvent<?> processNotificationEvent(NotificationEvent event) {
        return LogEvent.of("unknown", null);
    }

    @Override
    public LogEvent<?> processSetuidEvent(SetuidEvent event) {
        return LogEvent.of("/setuid", event.getBidder() + ":" + event.getUid() + ":" + event.getSuccess());
    }

    @Override
    public LogEvent<?> processVideoEvent(VideoEvent event) {
        return LogEvent.of("/openrtb2/video", event.getBidResponse());
    }
}
