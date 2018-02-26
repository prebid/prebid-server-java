package org.prebid.server.cookie;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.model.UidWithExpiry;
import org.prebid.server.model.Uids;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class UidsCookieService {

    private static final Logger logger = LoggerFactory.getLogger(UidsCookie.class);

    private static final String COOKIE_NAME = "uids";

    private final String optOutCookieName;
    private final String optOutCookieValue;
    private final String hostCookieFamily;
    private final String hostCookieName;
    private final String hostCookieDomain;
    private final Long ttlSeconds;

    public UidsCookieService(String optOutCookieName, String optOutCookieValue, String hostCookieFamily,
                             String hostCookieName, String hostCookieDomain, Integer ttlDays) {
        this.optOutCookieName = optOutCookieName;
        this.optOutCookieValue = optOutCookieValue;
        this.hostCookieFamily = hostCookieFamily;
        this.hostCookieName = hostCookieName;
        this.hostCookieDomain = hostCookieDomain;
        this.ttlSeconds = Duration.ofDays(ttlDays).getSeconds();
    }

    public UidsCookie parseFromRequest(RoutingContext context) {
        Objects.requireNonNull(context);

        Uids uids = null;
        final Cookie uidsCookie = context.getCookie(COOKIE_NAME);
        if (uidsCookie != null) {
            try {
                uids = Json.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(uidsCookie.getValue())),
                        Uids.class);
            } catch (IllegalArgumentException | DecodeException e) {
                logger.debug("Could not decode or parse {0} cookie value {1}", COOKIE_NAME, uidsCookie.getValue(), e);
            }
        }

        if (uids == null) {
            uids = Uids.builder()
                    .uids(Collections.emptyMap())
                    .bday(ZonedDateTime.now(Clock.systemUTC()))
                    .build();
        }

        if (uids.getUids() == null) {
            uids = uids.toBuilder()
                    .uids(Collections.emptyMap())
                    .build();
        }

        final Map<String, String> uidsLegacy = uids.getUidsLegacy();
        if (uids.getUids().isEmpty() && uidsLegacy != null) {
            final Map<String, UidWithExpiry> convertedUids = uidsLegacy.entrySet().stream().collect(
                    Collectors.toMap(Map.Entry::getKey, value -> UidWithExpiry.expired(value.getValue())));
            uids = uids.toBuilder()
                    .uids(convertedUids)
                    .uidsLegacy(Collections.emptyMap())
                    .build();
        }

        final String hostCookie = parseHostCookie(context);
        final boolean isOptedOut = isOptedOut(context);

        if (uids.getUids().get(hostCookieFamily) == null && hostCookie != null && !isOptedOut) {
            final Map<String, UidWithExpiry> uidsWithHostCookie = new HashMap<>(uids.getUids());
            uidsWithHostCookie.put(hostCookieFamily, UidWithExpiry.live(hostCookie));
            uids = uids.toBuilder().uids(uidsWithHostCookie).build();
        }

        if (isOptedOut) {
            uids = uids.toBuilder()
                    .optout(true)
                    .uids(Collections.emptyMap())
                    .build();
        }

        uids.getUids().entrySet().removeIf(entry ->
                UidsCookie.isFacebookSentinel(entry.getKey(), entry.getValue().getUid())
                        || StringUtils.isEmpty(entry.getValue().getUid()));

        return new UidsCookie(uids);
    }

    public Cookie toCookie(UidsCookie uidsCookie) {
        final Cookie cookie = Cookie
                .cookie(COOKIE_NAME, Base64.getUrlEncoder().encodeToString(uidsCookie.toJson().getBytes()))
                .setMaxAge(ttlSeconds);

        if (StringUtils.isNotBlank(hostCookieDomain)) {
            cookie.setDomain(hostCookieDomain);
        }

        return cookie;
    }

    public String parseHostCookie(RoutingContext context) {
        final Cookie cookie = hostCookieName != null ? context.getCookie(hostCookieName) : null;
        return cookie != null ? cookie.getValue() : null;
    }

    private boolean isOptedOut(RoutingContext context) {
        if (StringUtils.isNotBlank(optOutCookieName) && StringUtils.isNotBlank(optOutCookieValue)) {
            final Cookie cookie = context.getCookie(optOutCookieName);
            return cookie != null && Objects.equals(cookie.getValue(), optOutCookieValue);
        }
        return false;
    }
}
