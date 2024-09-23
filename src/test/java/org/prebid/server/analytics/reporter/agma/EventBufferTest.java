package org.prebid.server.analytics.reporter.agma;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class EventBufferTest {

    @Test
    public void pollToFlushShouldReturnEventsToFlushWhenMaxEventsExceeded() {
        // given
        final EventBuffer<String> target = new EventBuffer<>(1, 999);
        target.put("test", 4);

        // when and then
        assertThat(target.pollToFlush()).containsExactly("test");
    }

    @Test
    public void pollToFlushShouldReturnEventsToFlushWhenMaxBytesExceeded() {
        // given
        final EventBuffer<String> target = new EventBuffer<>(999, 1);
        target.put("test", 4);

        // when and then
        assertThat(target.pollToFlush()).containsExactly("test");
    }

    @Test
    public void pollToFlushShouldNotReturnAnyEventsWhenLimitsAreNotExceeded() {
        // given
        final EventBuffer<String> target = new EventBuffer<>(999, 999);
        target.put("test", 4);

        // when and then
        assertThat(target.pollToFlush()).isEmpty();
    }

    @Test
    public void pollAllShouldReturnAllEvents() {
        // given
        final EventBuffer<String> target = new EventBuffer<>(999, 999);
        target.put("test", 4);

        // when and then
        assertThat(target.pollAll()).containsExactly("test");
    }
}
