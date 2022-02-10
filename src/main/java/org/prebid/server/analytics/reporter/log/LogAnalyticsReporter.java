package org.prebid.server.analytics.reporter.log;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.analytics.reporter.log.model.LogEvent;
import org.prebid.server.json.JacksonMapper;

import java.util.Objects;

/**
 * {@link AnalyticsReporter} implementation that writes application events to a log, for illustration purpose only.
 */
public class LogAnalyticsReporter implements AnalyticsReporter {

    public static final Logger logger = LoggerFactory.getLogger(LogAnalyticsReporter.class);

    private final JacksonMapper mapper;

    public LogAnalyticsReporter(JacksonMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public <T> Future<Void> processEvent(T event) {
        final LogEvent<?> logEvent;

        if (event instanceof AmpEvent) {
            logEvent = LogEvent.of("/openrtb2/amp", ((AmpEvent) event).getBidResponse());
        } else if (event instanceof AuctionEvent) {
            logEvent = LogEvent.of("/openrtb2/auction", ((AuctionEvent) event).getBidResponse());
        } else if (event instanceof CookieSyncEvent) {
            logEvent = LogEvent.of("/cookie_sync", ((CookieSyncEvent) event).getBidderStatus());
        } else if (event instanceof NotificationEvent) {
            final NotificationEvent notificationEvent = (NotificationEvent) event;
            logEvent = LogEvent.of("/event", notificationEvent.getType() + notificationEvent.getBidId());
        } else if (event instanceof SetuidEvent) {
            final SetuidEvent setuidEvent = (SetuidEvent) event;
            logEvent = LogEvent.of(
                    "/setuid",
                    setuidEvent.getBidder() + ":" + setuidEvent.getUid() + ":" + setuidEvent.getSuccess());
        } else if (event instanceof VideoEvent) {
            logEvent = LogEvent.of("/openrtb2/video", ((VideoEvent) event).getBidResponse());
        } else {
            logEvent = LogEvent.of("unknown", null);
        }

        logger.debug(mapper.encodeToString(logEvent));

        return Future.succeededFuture();
    }

    @Override
    public int vendorId() {
        return 0;
    }

    @Override
    public String name() {
        return "logAnalytics";
    }
}
