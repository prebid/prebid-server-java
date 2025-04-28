package org.prebid.server.cookie.model;

import lombok.Value;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Bundles the UID with an Expiration date. After the expiration, the UID is no longer valid.
 */
@Value
public class UidWithExpiry {

    private static final long LIVE_TTL_MS = Duration.ofDays(14).toMillis();
    private static final long EXPIRED_TTL_MS = Duration.ofMinutes(5).toMillis();

    String uid;

    ZonedDateTime expires;

    public static UidWithExpiry live(String uid) {
        return create(uid, LIVE_TTL_MS);
    }

    public static UidWithExpiry expired(String uid) {
        return create(uid, -EXPIRED_TTL_MS);
    }

    private static UidWithExpiry create(String uid, long ttlMs) {
        return new UidWithExpiry(uid, ZonedDateTime.now(Clock.systemUTC()).plus(ttlMs, ChronoUnit.MILLIS));
    }
}
