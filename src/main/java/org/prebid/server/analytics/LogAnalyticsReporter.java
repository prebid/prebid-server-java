package org.prebid.server.analytics;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.AllArgsConstructor;
import lombok.Value;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.model.VideoEvent;
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
    public <T> void processEvent(T event) {
        final LogEvent<?> logEvent;

        if (event instanceof AuctionEvent) {
            logEvent = new LogEvent<>("/openrtb2/auction", ((AuctionEvent) event).getBidResponse());
        } else if (event instanceof AmpEvent) {
            logEvent = new LogEvent<>("/openrtb2/amp", ((AmpEvent) event).getBidResponse());
        } else if (event instanceof VideoEvent) {
            logEvent = new LogEvent<>("/openrtb2/video", ((VideoEvent) event).getBidResponse());
        } else if (event instanceof SetuidEvent) {
            final SetuidEvent setuidEvent = (SetuidEvent) event;
            logEvent = new LogEvent<>(
                    "/setuid",
                    setuidEvent.getBidder() + ":" + setuidEvent.getUid() + ":" + setuidEvent.getSuccess());
        } else if (event instanceof CookieSyncEvent) {
            logEvent = new LogEvent<>("/cookie_sync", ((CookieSyncEvent) event).getBidderStatus());
        } else {
            logEvent = new LogEvent<>("unknown", null);
        }

        logger.debug(mapper.encode(logEvent));
    }

    @AllArgsConstructor
    @Value
    private static class LogEvent<T> {

        String type;

        @JsonUnwrapped
        T event;
    }
}
