package org.prebid.server.analytics.reporter.log;

import org.junit.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.analytics.model.AmpEvent;
import org.prebid.server.analytics.model.AuctionEvent;
import org.prebid.server.analytics.model.CookieSyncEvent;
import org.prebid.server.analytics.model.SetuidEvent;
import org.prebid.server.analytics.model.VideoEvent;

public class LogAnalyticsReporterTest extends VertxTest {

    @Test
    public void shouldLogEvent() {
        // dumb test to trigger coverage
        final LogAnalyticsReporter reporter = new LogAnalyticsReporter(jacksonMapper);
        reporter.processEvent(AuctionEvent.builder().build());
        reporter.processEvent(AmpEvent.builder().build());
        reporter.processEvent(VideoEvent.builder().build());
        reporter.processEvent(SetuidEvent.builder().build());
        reporter.processEvent(CookieSyncEvent.builder().build());
    }
}
