package org.prebid.server.analytics;

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
        reporter.processEvent(AuctionEvent.builder().build(), false);
        reporter.processEvent(AmpEvent.builder().build(), false);
        reporter.processEvent(VideoEvent.builder().build(), false);
        reporter.processEvent(SetuidEvent.builder().build(), false);
        reporter.processEvent(CookieSyncEvent.builder().build(), false);
    }

    @Test
    public void shouldNotLogEvent() {
        final LogAnalyticsReporter reporter = new LogAnalyticsReporter(jacksonMapper);
        reporter.processEvent(AuctionEvent.builder().build(), true);
        reporter.processEvent(AmpEvent.builder().build(), true);
        reporter.processEvent(VideoEvent.builder().build(), true);
        reporter.processEvent(SetuidEvent.builder().build(), true);
        reporter.processEvent(CookieSyncEvent.builder().build(), true);
    }
}
