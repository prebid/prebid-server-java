package org.prebid.server.deals.model;

import org.junit.Test;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceDeal;
import org.prebid.server.proto.openrtb.ext.response.ExtTraceDeal.Category;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class DeepDebugLogTest {

    private final Clock clock = Clock.fixed(Instant.now(), ZoneId.systemDefault());

    @SuppressWarnings("unchecked")
    @Test
    public void addShouldNotRecordMessageWhenDeepDebugIsDisabled() {
        // given
        final DeepDebugLog deepDebugLog = DeepDebugLog.create(false, clock);

        final Supplier<String> messageSupplier = (Supplier<String>) mock(Supplier.class);

        // when
        deepDebugLog.add(null, Category.pacing, messageSupplier);

        // then
        verify(messageSupplier, never()).get();

        assertThat(deepDebugLog.entries()).isEmpty();
    }

    @Test
    public void addShouldRecordMessageWhenDeepDebugIsEnabled() {
        // given
        final DeepDebugLog deepDebugLog = DeepDebugLog.create(true, clock);

        // when
        deepDebugLog.add(null, Category.pacing, () -> "debug message 1");

        // then
        assertThat(deepDebugLog.entries()).containsOnly(
                ExtTraceDeal.of(null, ZonedDateTime.now(clock), Category.pacing, "debug message 1"));
    }
}
