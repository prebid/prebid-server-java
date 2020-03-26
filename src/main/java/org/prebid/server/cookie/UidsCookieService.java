package org.prebid.server.cookie;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;

import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Contains logic for obtaining UIDs from the request and actualizing them.
 */
public class UidsCookieService {

    private static final Logger logger = LoggerFactory.getLogger(UidsCookieService.class);

    private static final String COOKIE_NAME = "uids";
    private static final int MIN_COOKIE_SIZE_BYTES = 500;

    private final String optOutCookieName;
    private final String optOutCookieValue;
    private final String hostCookieFamily;
    private final String hostCookieName;
    private final String hostCookieDomain;
    private final long ttlSeconds;
    private final int maxCookieSizeBytes;
    private final JacksonMapper mapper;

    public UidsCookieService(String optOutCookieName,
                             String optOutCookieValue,
                             String hostCookieFamily,
                             String hostCookieName,
                             String hostCookieDomain,
                             int ttlDays,
                             int maxCookieSizeBytes,
                             JacksonMapper mapper) {

        if (maxCookieSizeBytes != 0 && maxCookieSizeBytes < MIN_COOKIE_SIZE_BYTES) {
            throw new IllegalArgumentException(String.format(
                    "Configured cookie size is less than allowed minimum size of %d", maxCookieSizeBytes));
        }

        this.optOutCookieName = optOutCookieName;
        this.optOutCookieValue = optOutCookieValue;
        this.hostCookieFamily = hostCookieFamily;
        this.hostCookieName = hostCookieName;
        this.hostCookieDomain = hostCookieDomain;
        this.ttlSeconds = Duration.ofDays(ttlDays).getSeconds();
        this.maxCookieSizeBytes = maxCookieSizeBytes;
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Retrieves UIDs cookie (base64 encoded) value from http request and transforms it into {@link UidsCookie}.
     * <p>
     * Uids cookie value from http request may be represented in accordance with one of two formats:
     * <ul>
     * <li>Legacy cookies - had UIDs without expiration dates</li>
     * <li>Current cookies - always include UIDs with expiration dates</li>
     * </ul>
     * If request contains 'legacy' UIDs cookie format then it will be interpreted as already expired and forced
     * to re-sync
     * <p>
     * This method also sets 'hostCookieFamily' if 'hostCookie' is present in the request and feature is not opted-out.
     * If feature is opted-out uids attribute will be blank.
     * <p>
     * Note: UIDs will be excluded from resulting {@link UidsCookie} if their value are 'null'.
     */
    public UidsCookie parseFromRequest(RoutingContext context) {
        return parseFromCookies(HttpUtil.cookiesAsMap(context));
    }

    /**
     * Retrieves UIDs cookie (base64 encoded) value from cookies map and transforms it into {@link UidsCookie}.
     */
    UidsCookie parseFromCookies(Map<String, String> cookies) {
        final Uids parsedUids = parseUids(cookies);

        final Uids.UidsBuilder uidsBuilder = Uids.builder()
                .uidsLegacy(Collections.emptyMap())
                .bday(parsedUids != null ? parsedUids.getBday() : ZonedDateTime.now(Clock.systemUTC()));

        final Boolean optout;
        final Map<String, UidWithExpiry> uidsMap;

        if (isOptedOut(cookies)) {
            optout = true;
            uidsMap = Collections.emptyMap();
        } else {
            optout = parsedUids != null ? parsedUids.getOptout() : null;
            uidsMap = enrichAndSanitizeUids(parsedUids, cookies);
        }

        return new UidsCookie(uidsBuilder.uids(uidsMap).optout(optout).build(), mapper);
    }

    /**
     * Parses cookies {@link Map} and composes {@link Uids} model.
     */
    public Uids parseUids(Map<String, String> cookies) {
        if (cookies.containsKey(COOKIE_NAME)) {
            final String cookieValue = cookies.get(COOKIE_NAME);
            try {
                return mapper.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(cookieValue)), Uids.class);
            } catch (IllegalArgumentException | DecodeException e) {
                logger.debug("Could not decode or parse {0} cookie value {1}", e, COOKIE_NAME, cookieValue);
            }
        }
        return null;
    }

    /**
     * Creates a {@link Cookie} with 'uids' as a name and encoded JSON string representing supplied {@link UidsCookie}
     * as a value.
     */
    public Cookie toCookie(UidsCookie uidsCookie) {
        UidsCookie modifiedUids = uidsCookie;
        byte[] cookieBytes = uidsCookie.toJson().getBytes();

        while (maxCookieSizeBytes > 0 && cookieBytes.length > maxCookieSizeBytes) {
            final String familyName = modifiedUids.getCookieUids().getUids().entrySet().stream()
                    .reduce(UidsCookieService::getClosestExpiration)
                    .map(Map.Entry::getKey)
                    .orElse(null);
            modifiedUids = modifiedUids.deleteUid(familyName);
            cookieBytes = modifiedUids.toJson().getBytes();
        }

        final Cookie cookie = Cookie
                .cookie(COOKIE_NAME, Base64.getUrlEncoder().encodeToString(cookieBytes))
                .setPath("/")
                .setMaxAge(ttlSeconds);

        if (StringUtils.isNotBlank(hostCookieDomain)) {
            cookie.setDomain(hostCookieDomain);
        }

        return cookie;
    }

    /**
     * Returns the Uid with the closest expiration date, e.i. the one that will expire sooner.
     */
    private static Map.Entry<String, UidWithExpiry> getClosestExpiration(Map.Entry<String, UidWithExpiry> first,
                                                                         Map.Entry<String, UidWithExpiry> second) {
        return first.getValue().getExpires().isBefore(second.getValue().getExpires()) ? first : second;
    }

    /**
     * Lookups host cookie value from cookies map by configured host cookie name.
     */
    public String parseHostCookie(Map<String, String> cookies) {
        return hostCookieName != null ? cookies.get(hostCookieName) : null;
    }

    /**
     * Returns configured host cookie family.
     */
    public String getHostCookieFamily() {
        return hostCookieFamily;
    }

    /**
     * Checks incoming request if it matches pre-configured opted-out cookie name, value and de-activates
     * UIDs cookie sync.
     */
    private boolean isOptedOut(Map<String, String> cookies) {
        if (StringUtils.isNotBlank(optOutCookieName) && StringUtils.isNotBlank(optOutCookieValue)) {
            final String cookieValue = cookies.get(optOutCookieName);
            return cookieValue != null && Objects.equals(cookieValue, optOutCookieValue);
        }
        return false;
    }

    /**
     * Enriches {@link Uids} parsed from request cookies with uid from host cookie (if applicable) and removes
     * invalid uids. Also converts legacy uids to uids with expiration.
     */
    private Map<String, UidWithExpiry> enrichAndSanitizeUids(Uids uids, Map<String, String> cookies) {
        final Map<String, UidWithExpiry> originalUidsMap = uids != null ? uids.getUids() : null;
        final Map<String, UidWithExpiry> workingUidsMap = new HashMap<>(
                ObjectUtils.defaultIfNull(originalUidsMap, Collections.emptyMap()));

        final Map<String, String> legacyUids = uids != null ? uids.getUidsLegacy() : null;
        if (workingUidsMap.isEmpty() && legacyUids != null) {
            legacyUids.forEach((key, value) -> workingUidsMap.put(key, UidWithExpiry.expired(value)));
        }

        final String hostCookie = parseHostCookie(cookies);
        if (hostCookie != null && hostCookieDiffers(hostCookie, workingUidsMap.get(hostCookieFamily))) {
            // make host cookie precedence over uids
            workingUidsMap.put(hostCookieFamily, UidWithExpiry.live(hostCookie));
        }

        workingUidsMap.entrySet().removeIf(UidsCookieService::facebookSentinelOrEmpty);

        return workingUidsMap;
    }

    /**
     * Returns true if host cookie value differs from the given UID value.
     */
    private static boolean hostCookieDiffers(String hostCookie, UidWithExpiry uid) {
        return uid == null || !Objects.equals(hostCookie, uid.getUid());
    }

    private static boolean facebookSentinelOrEmpty(Map.Entry<String, UidWithExpiry> entry) {
        return UidsCookie.isFacebookSentinel(entry.getKey(), entry.getValue().getUid())
                || StringUtils.isEmpty(entry.getValue().getUid());
    }
}
