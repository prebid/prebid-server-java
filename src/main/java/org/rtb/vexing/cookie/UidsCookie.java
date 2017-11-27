package org.rtb.vexing.cookie;

import io.vertx.core.json.Json;
import io.vertx.ext.web.Cookie;
import org.rtb.vexing.model.Uids;

import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class UidsCookie {

    static final String COOKIE_NAME = "uids";
    private static final long COOKIE_EXPIRATION = Duration.ofDays(180).getSeconds();

    private final Uids uids;

    public UidsCookie(Uids uids) {
        this.uids = Objects.requireNonNull(uids);
    }

    public static boolean isFacebookSentinel(String familyName, String uid) {
        Objects.requireNonNull(familyName);
        Objects.requireNonNull(uid);
        return familyName.equals("audienceNetwork") && uid.equals("0");
    }

    public String uidFrom(String familyName) {
        return uids.uids != null ? uids.uids.get(familyName) : null;
    }

    public boolean allowsSync() {
        return !Objects.equals(uids.optout, Boolean.TRUE);
    }

    public boolean hasLiveUids() {
        // FIXME: this will have something to do with uids expiration eventually, legacy cookie are considered
        // already expired
        return false;
    }

    public UidsCookie deleteUid(String familyName) {
        Objects.requireNonNull(familyName);

        final UidsCookie result;
        if (uids.uids == null) {
            result = this;
        } else {
            final Map<String, String> uidsMap = new HashMap<>(uids.uids);
            uidsMap.remove(familyName);
            result = new UidsCookie(uids.toBuilder().uids(uidsMap).build());
        }

        return result;
    }

    public UidsCookie updateUid(String familyName, String uid) {
        Objects.requireNonNull(familyName);
        Objects.requireNonNull(uid);
        final Map<String, String> uidsMap = uids.uids != null ? new HashMap<>(uids.uids) : new HashMap<>();
        uidsMap.put(familyName, uid);
        return new UidsCookie(uids.toBuilder().uids(uidsMap).build());
    }

    public Cookie toCookie() {
        return Cookie.cookie(COOKIE_NAME, Base64.getUrlEncoder().encodeToString(Json.encodeToBuffer(uids).getBytes()))
                .setMaxAge(COOKIE_EXPIRATION);
    }
}
