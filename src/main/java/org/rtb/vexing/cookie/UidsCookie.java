package org.rtb.vexing.cookie;

import io.vertx.core.json.Json;
import org.rtb.vexing.model.UidWithExpiry;
import org.rtb.vexing.model.Uids;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class UidsCookie {

    private final Uids uids;

    public UidsCookie(Uids uids) {
        this.uids = Objects.requireNonNull(uids);
        Objects.requireNonNull(uids.uids); // without uids doesn't make sense
    }

    public static boolean isFacebookSentinel(String familyName, String uid) {
        Objects.requireNonNull(familyName);
        Objects.requireNonNull(uid);

        return familyName.equals("audienceNetwork") && uid.equals("0");
    }

    public String uidFrom(String familyName) {
        Objects.requireNonNull(familyName);

        final UidWithExpiry uid = getUid(familyName);
        return uid != null ? uid.uid : null;
    }

    public boolean allowsSync() {
        return !Objects.equals(uids.optout, Boolean.TRUE);
    }

    public boolean hasLiveUids() {
        return uids.uids.values().stream().anyMatch(UidsCookie::isLive);
    }

    public boolean hasLiveUidFrom(String familyName) {
        Objects.requireNonNull(familyName);

        final UidWithExpiry uid = getUid(familyName);
        return uid != null && uid.uid != null && isLive(uid);
    }

    public UidsCookie deleteUid(String familyName) {
        Objects.requireNonNull(familyName);

        final Map<String, UidWithExpiry> uidsMap = new HashMap<>(uids.uids);
        uidsMap.remove(familyName);
        return new UidsCookie(uids.toBuilder().uids(uidsMap).build());
    }

    public UidsCookie updateUid(String familyName, String uid) {
        Objects.requireNonNull(familyName);
        Objects.requireNonNull(uid);

        final Map<String, UidWithExpiry> uidsMap = uids.uids != null ? new HashMap<>(uids.uids) : new HashMap<>();
        uidsMap.put(familyName, UidWithExpiry.live(uid));
        return new UidsCookie(uids.toBuilder().uids(uidsMap).build());
    }

    public UidsCookie updateOptout(boolean optout) {
        final Uids.UidsBuilder uidsBuilder = uids.toBuilder();
        uidsBuilder.optout(optout);
        if (optout) {
            uidsBuilder.uids(Collections.emptyMap());
        }
        return new UidsCookie(uidsBuilder.build());
    }

    public String toJson() {
        return Json.encode(uids);
    }

    private UidWithExpiry getUid(String familyName) {
        return uids.uids != null ? uids.uids.get(familyName) : null;
    }

    private static boolean isLive(UidWithExpiry uid) {
        return uid.expires != null && uid.expires.isAfter(ZonedDateTime.now());
    }
}
