package org.prebid.server.model;

import org.junit.Test;
import org.prebid.server.cookie.model.UidWithExpiry;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

public class UidWithExpiryTest {

    @Test
    public void shouldCreateLiveUid() {
        // when
        final UidWithExpiry uid = UidWithExpiry.live("12345");

        // then
        assertThat(uid.getUid()).isEqualTo("12345");
        assertThat(uid.getExpires().toInstant())
                .isCloseTo(Instant.now().plus(14, ChronoUnit.DAYS), within(10, ChronoUnit.SECONDS));
    }

    @Test
    public void shouldCreateExpiredUid() {
        // when
        final UidWithExpiry uid = UidWithExpiry.expired("12345");

        // then
        assertThat(uid.getUid()).isEqualTo("12345");
        assertThat(uid.getExpires().toInstant())
                .isCloseTo(Instant.now().minus(5, ChronoUnit.MINUTES), within(10, ChronoUnit.SECONDS));
    }
}
