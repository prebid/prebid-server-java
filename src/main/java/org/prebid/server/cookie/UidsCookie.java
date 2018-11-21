package org.prebid.server.cookie;

import io.vertx.core.json.Json;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Holds logic for manipulating {@link Uids}
 */
public class UidsCookie {

    private final Uids uids;

    public UidsCookie(Uids uids) {
        this.uids = Objects.requireNonNull(uids);
        Objects.requireNonNull(uids.getUids()); // without uids doesn't make sense
    }

    /**
     * Returns false if 'audienceNetwork' UID has a "0" value which is illegitimate in prebid server
     * otherwise true
     */
    public static boolean isFacebookSentinel(String familyName, String uid) {
        return Objects.equals(familyName, "audienceNetwork") && Objects.equals(uid, "0");
    }

    /**
     * Returns a UID value for given family name.
     */
    public String uidFrom(String familyName) {
        final UidWithExpiry uid = uids.getUids().get(familyName);
        return uid != null ? uid.getUid() : null;
    }

    /**
     * Checks if {@link UidsCookie} is not 'opted-out'.
     */
    public boolean allowsSync() {
        return !Objects.equals(uids.getOptout(), Boolean.TRUE);
    }

    /**
     * Returns true if any UID value is 'live'.
     */
    public boolean hasLiveUids() {
        return uids.getUids().values().stream().anyMatch(UidsCookie::isLive);
    }

    /**
     * Returns true if UID value for given family name is 'live'.
     */
    public boolean hasLiveUidFrom(String familyName) {
        final UidWithExpiry uid = uids.getUids().get(familyName);
        return uid != null && uid.getUid() != null && isLive(uid);
    }

    /**
     * Performs deletion of UID value by family name and returns newly constructed {@link UidsCookie}
     * to avoid mutation of the current {@link UidsCookie} object.
     */
    public UidsCookie deleteUid(String familyName) {
        final Map<String, UidWithExpiry> uidsMap = new HashMap<>(uids.getUids());
        uidsMap.remove(familyName);
        return new UidsCookie(uids.toBuilder().uids(uidsMap).build());
    }

    /**
     * Performs updates of UID value by family name and returns newly constructed {@link UidsCookie}
     * to avoid mutation of the current {@link UidsCookie}.
     */
    public UidsCookie updateUid(String familyName, String uid) {
        final Map<String, UidWithExpiry> uidsMap = new HashMap<>(uids.getUids());
        uidsMap.put(familyName, UidWithExpiry.live(uid));
        return new UidsCookie(uids.toBuilder().uids(uidsMap).build());
    }

    /**
     * Performs updates of {@link UidsCookie}'s optout flag and returns newly constructed {@link UidsCookie}
     * to avoid mutation of the current {@link UidsCookie}.
     */
    public UidsCookie updateOptout(boolean optout) {
        final Uids.UidsBuilder uidsBuilder = uids.toBuilder();
        uidsBuilder.optout(optout);
        if (optout) {
            uidsBuilder.uids(Collections.emptyMap());
        }
        return new UidsCookie(uidsBuilder.build());
    }

    /**
     * Converts {@link Uids} to JSON string.
     */
    String toJson() {
        return Json.encode(uids);
    }

    /**
     * Returns true if UID expires date in future otherwise false
     */
    private static boolean isLive(UidWithExpiry uid) {
        final ZonedDateTime expires = uid.getExpires();
        return expires != null && expires.isAfter(ZonedDateTime.now());
    }
}
