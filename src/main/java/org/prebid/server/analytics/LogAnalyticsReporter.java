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
        final String type;
        if (event instanceof AuctionEvent) {
            type = "/openrtb2/auction";
        } else if (event instanceof AmpEvent) {
            type = "/openrtb2/amp";
        } else if (event instanceof VideoEvent) {
            type = "/openrtb2/video";
        } else if (event instanceof SetuidEvent) {
            type = "/setuid";
        } else if (event instanceof CookieSyncEvent) {
            type = "/cookie_sync";
        } else {
            type = "unknown";
        }

        logger.debug(mapper.encode(new LogEvent<>(type, event)));
    }

    @AllArgsConstructor
    @Value
    private static class LogEvent<T> {

        String type;

        @JsonUnwrapped
        T event;
    }
}
