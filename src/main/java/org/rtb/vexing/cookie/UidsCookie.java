package org.rtb.vexing.cookie;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.rtb.vexing.model.Uids;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class UidsCookie {

    private static final Logger logger = LoggerFactory.getLogger(UidsCookie.class);

    private static final String COOKIE_NAME = "uids";
    private static final long COOKIE_EXPIRATION = Duration.ofDays(180).getSeconds();
    private static final DateTimeFormatter BDAY_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendInstant(9)
            .toFormatter();

    private final Uids uids;

    private UidsCookie(Uids uids) {
        this.uids = uids;
    }

    public static UidsCookie parseFromRequest(RoutingContext context) {
        Objects.requireNonNull(context);
        Uids uids = null;
        final Cookie uidsCookie = context.getCookie(UidsCookie.COOKIE_NAME);
        if (uidsCookie != null) {
            try {
                uids = Json.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(uidsCookie.getValue())),
                        Uids.class);
            } catch (IllegalArgumentException | DecodeException e) {
                logger.debug("Could not decode or parse {0} cookie value {1}", UidsCookie.COOKIE_NAME,
                        uidsCookie.getValue(), e);
            }
        }
        return new UidsCookie(uids != null ? uids : Uids.builder()
                .uids(Collections.emptyMap())
                .bday(BDAY_FORMATTER.format(Instant.now()))
                .build());
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

    public UidsCookie deleteUid(String familyName) {
        Objects.requireNonNull(familyName);
        final Map<String, String> uidsMap = new HashMap<>(uids.uids);
        uidsMap.remove(familyName);
        return new UidsCookie(uids.toBuilder().uids(uidsMap).build());
    }

    public UidsCookie updateUid(String familyName, String uid) {
        Objects.requireNonNull(familyName);
        Objects.requireNonNull(uid);
        final Map<String, String> uidsMap = new HashMap<>(uids.uids);
        uidsMap.put(familyName, uid);
        return new UidsCookie(uids.toBuilder().uids(uidsMap).build());
    }

    public Cookie toCookie() {
        return Cookie.cookie(COOKIE_NAME, Base64.getUrlEncoder().encodeToString(Json.encodeToBuffer(uids).getBytes()))
                .setMaxAge(COOKIE_EXPIRATION);
    }
}
