package org.rtb.vexing.cookie;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.model.UidWithExpiry;
import org.rtb.vexing.model.Uids;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class UidsCookieFactory {

    private static final Logger logger = LoggerFactory.getLogger(UidsCookie.class);

    private static final DateTimeFormatter BDAY_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendInstant(9)
            .toFormatter();

    private final String optOutCookieName;
    private final String optOutCookieValue;
    private final String hostCookieFamily;
    private final String hostCookieName;

    private UidsCookieFactory(String optOutCookieName, String optOutCookieValue, String hostCookieFamily,
                              String hostCookieName) {
        this.optOutCookieName = optOutCookieName;
        this.optOutCookieValue = optOutCookieValue;
        this.hostCookieFamily = hostCookieFamily;
        this.hostCookieName = hostCookieName;
    }

    public static UidsCookieFactory create(ApplicationConfig config) {
        Objects.requireNonNull(config);

        return new UidsCookieFactory(
                config.getString("host_cookie.optout_cookie.name", null),
                config.getString("host_cookie.optout_cookie.value", null),
                config.getString("host_cookie.family", null),
                config.getString("host_cookie.cookie_name", null)
        );
    }

    public UidsCookie parseFromRequest(RoutingContext context) {
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

        if (uids == null) {
            uids = Uids.builder()
                    .uids(Collections.emptyMap())
                    .bday(BDAY_FORMATTER.format(Instant.now()))
                    .build();
        }

        if (uids.uids == null) {
            uids = uids.toBuilder()
                    .uids(Collections.emptyMap())
                    .build();
        }

        if (uids.uids.isEmpty() && uids.uidsLegacy != null) {
            final Map<String, UidWithExpiry> convertedUids = uids.uidsLegacy.entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getKey, value -> UidWithExpiry.expired(value.getValue())));
            uids = uids.toBuilder()
                    .uids(convertedUids)
                    .uidsLegacy(Collections.emptyMap())
                    .build();
        }

        final Cookie hostCookie = hostCookieName != null ? context.getCookie(hostCookieName) : null;
        final boolean isOptedOut = isOptedOut(context);

        if (uids.uids.get(hostCookieFamily) == null && hostCookie != null && !isOptedOut) {
            final Map<String, UidWithExpiry> uidsWithHostCookie = new HashMap<>(uids.uids);
            uidsWithHostCookie.put(hostCookieFamily, UidWithExpiry.live(hostCookie.getValue()));
            uids = uids.toBuilder().uids(uidsWithHostCookie).build();
        }

        if (isOptedOut) {
            uids = uids.toBuilder()
                    .optout(true)
                    .uids(Collections.emptyMap())
                    .build();
        }

        return new UidsCookie(uids);
    }

    private boolean isOptedOut(RoutingContext context) {
        if (StringUtils.isNotBlank(optOutCookieName) && StringUtils.isNotBlank(optOutCookieValue)) {
            final Cookie cookie = context.getCookie(optOutCookieName);
            return cookie != null && Objects.equals(cookie.getValue(), optOutCookieValue);
        }
        return false;
    }
}
