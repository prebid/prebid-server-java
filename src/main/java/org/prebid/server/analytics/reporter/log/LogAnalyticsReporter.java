package org.prebid.server.analytics.reporter.log;

import io.vertx.core.Future;
import org.prebid.server.analytics.AnalyticsReporter;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.model.NotificationEvent;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.model.VideoEvent;
import org.prebid.server.analytics.reporter.log.model.LogEvent;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;

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
        final LogEvent<?> logEvent = switch (event) {
            case AmpEvent ampEvent -> LogEvent.of("/openrtb2/amp", ampEvent.getBidResponse());
            case AuctionEvent auctionEvent -> LogEvent.of("/openrtb2/auction", auctionEvent.getBidResponse());
            case CookieSyncEvent cookieSyncEvent -> LogEvent.of("/cookie_sync", cookieSyncEvent.getBidderStatus());
            case NotificationEvent notificationEvent ->
                    LogEvent.of("/event", notificationEvent.getType() + notificationEvent.getBidId());
            case SetuidEvent setuidEvent -> LogEvent.of(
                    "/setuid",
                    setuidEvent.getBidder() + ":" + setuidEvent.getUid() + ":" + setuidEvent.getSuccess());
            case VideoEvent videoEvent -> LogEvent.of("/openrtb2/video", videoEvent.getBidResponse());
            case null, default -> LogEvent.of("unknown", null);
        };

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
