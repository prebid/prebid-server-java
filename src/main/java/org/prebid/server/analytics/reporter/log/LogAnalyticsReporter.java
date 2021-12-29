package org.prebid.server.analytics.reporter.log;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.analytics.model.AnalyticsEvent;
import org.prebid.server.analytics.processor.LogAnalyticsEventProcessor;
import org.prebid.server.analytics.reporter.AnalyticsReporter;
import org.prebid.server.analytics.reporter.log.model.LogEvent;
import org.prebid.server.json.JacksonMapper;

import java.util.Objects;

/**
 * {@link AnalyticsReporter} implementation that writes application events to a log, for illustration purpose only.
 */
public class LogAnalyticsReporter implements AnalyticsReporter {

    public static final Logger logger = LoggerFactory.getLogger(LogAnalyticsReporter.class);

    private final LogAnalyticsEventProcessor eventProcessor;
    private final JacksonMapper mapper;

    public LogAnalyticsReporter(LogAnalyticsEventProcessor eventProcessor, JacksonMapper mapper) {
        this.eventProcessor = Objects.requireNonNull(eventProcessor);
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Future<Void> processEvent(AnalyticsEvent event) {
        final LogEvent<?> logEvent = event.accept(eventProcessor);
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
