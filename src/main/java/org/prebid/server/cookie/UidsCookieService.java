package org.prebid.server.cookie;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.metric.Metrics;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.util.HttpUtil;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Contains logic for obtaining UIDs from the request and actualizing them.
 */
public class UidsCookieService {

    private static final Logger logger = LoggerFactory.getLogger(UidsCookieService.class);

    private static final String COOKIE_NAME = "uids";
    private static final String COOKIE_NAME_FORMAT = "uids%d";
    private static final int MIN_COOKIE_SIZE_BYTES = 500;
    private static final int MIN_NUMBER_OF_UID_COOKIES = 1;
    private static final int MAX_NUMBER_OF_UID_COOKIES = 30;

    private final String optOutCookieName;
    private final String optOutCookieValue;
    private final String hostCookieFamily;
    private final String hostCookieName;
    private final String hostCookieDomain;
    private final long ttlSeconds;

    private final int maxCookieSizeBytes;
    private final int numberOfUidCookies;

    private final PrioritizedCoopSyncProvider prioritizedCoopSyncProvider;
    private final Metrics metrics;
    private final JacksonMapper mapper;

    public UidsCookieService(String optOutCookieName,
                             String optOutCookieValue,
                             String hostCookieFamily,
                             String hostCookieName,
                             String hostCookieDomain,
                             int ttlDays,
                             int maxCookieSizeBytes,
                             int numberOfUidCookies,
                             PrioritizedCoopSyncProvider prioritizedCoopSyncProvider,
                             Metrics metrics,
                             JacksonMapper mapper) {

        if (maxCookieSizeBytes != 0 && maxCookieSizeBytes < MIN_COOKIE_SIZE_BYTES) {
            throw new IllegalArgumentException(
                    "Configured cookie size is less than allowed minimum size of " + MIN_COOKIE_SIZE_BYTES);
        }

        if (numberOfUidCookies < MIN_NUMBER_OF_UID_COOKIES || numberOfUidCookies > MAX_NUMBER_OF_UID_COOKIES) {
            throw new IllegalArgumentException(
                    "Configured number of uid cookies should be in the range from %d to %d"
                            .formatted(MIN_NUMBER_OF_UID_COOKIES, MAX_NUMBER_OF_UID_COOKIES));
        }

        this.optOutCookieName = optOutCookieName;
        this.optOutCookieValue = optOutCookieValue;
        this.hostCookieFamily = hostCookieFamily;
        this.hostCookieName = hostCookieName;
        this.hostCookieDomain = StringUtils.isNotBlank(hostCookieDomain) ? hostCookieDomain : null;
        this.ttlSeconds = Duration.ofDays(ttlDays).getSeconds();
        this.maxCookieSizeBytes = maxCookieSizeBytes;
        this.numberOfUidCookies = numberOfUidCookies;
        this.prioritizedCoopSyncProvider = Objects.requireNonNull(prioritizedCoopSyncProvider);
        this.metrics = Objects.requireNonNull(metrics);
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
    public UidsCookie parseFromRequest(RoutingContext routingContext) {
        return parseFromCookies(HttpUtil.cookiesAsMap(routingContext));
    }

    public UidsCookie parseFromRequest(HttpRequestContext httpRequest) {
        return parseFromCookies(HttpUtil.cookiesAsMap(httpRequest));
    }

    /**
     * Retrieves UIDs cookie (base64 encoded) value from cookies map and transforms it into {@link UidsCookie}.
     */
    UidsCookie parseFromCookies(Map<String, String> cookies) {
        final Uids parsedUids = parseUids(cookies);
        final boolean isOptedOut = isOptedOut(cookies);

        final Uids uids = Uids.builder()
                .uids(isOptedOut ? Collections.emptyMap() : enrichAndSanitizeUids(parsedUids, cookies))
                .optout(isOptedOut)
                .build();

        return new UidsCookie(uids, mapper);
    }

    /**
     * Parses cookies {@link Map} and composes {@link Uids} model.
     */
    private Uids parseUids(Map<String, String> cookies) {
        final Map<String, UidWithExpiry> uids = new HashMap<>();

        for (Map.Entry<String, String> cookie : cookies.entrySet()) {
            final String cookieKey = cookie.getKey();
            if (!cookieKey.startsWith(COOKIE_NAME)) {
                continue;
            }

            try {
                final Uids parsedUids = mapper.decodeValue(
                        Buffer.buffer(Base64.getUrlDecoder().decode(cookie.getValue())), Uids.class);
                if (parsedUids != null && parsedUids.getUids() != null) {
                    parsedUids.getUids().forEach((key, value) -> uids.merge(key, value, (newValue, oldValue) ->
                            newValue.getExpires().compareTo(oldValue.getExpires()) > 0 ? newValue : oldValue));
                }
            } catch (IllegalArgumentException | DecodeException e) {
                logger.debug("Could not decode or parse {} cookie value {}", e, COOKIE_NAME, cookie.getValue());
            }
        }

        return Uids.builder().uids(uids).build();
    }

    /**
     * Creates a {@link Cookie} with 'uids' as a name and encoded JSON string representing supplied {@link UidsCookie}
     * as a value.
     */
    public Cookie aliveCookie(String cookieName, UidsCookie uidsCookie) {
        final String value = Base64.getUrlEncoder().encodeToString(uidsCookie.toJson().getBytes());
        return makeCookie(cookieName, value, ttlSeconds);
    }

    public Cookie aliveCookie(UidsCookie uidsCookie) {
        return aliveCookie(COOKIE_NAME, uidsCookie);
    }

    public Cookie expiredCookie(String cookieName) {
        return makeCookie(cookieName, StringUtils.EMPTY, 0);
    }

    private Cookie makeCookie(String cookieName, String value, long maxAge) {
        return Cookie.cookie(cookieName, value)
                .setPath("/")
                .setSameSite(CookieSameSite.NONE)
                .setSecure(true)
                .setMaxAge(maxAge)
                .setDomain(hostCookieDomain);
    }

    /**
     * Lookups host cookie value from cookies map by configured host cookie name.
     */
    public String parseHostCookie(Map<String, String> cookies) {
        return hostCookieName != null ? cookies.get(hostCookieName) : null;
    }

    /**
     * Lookups host cookie value from request context by configured host cookie name.
     */
    public String parseHostCookie(HttpRequestContext httpRequest) {
        return parseHostCookie(HttpUtil.cookiesAsMap(httpRequest));
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
        final Map<String, UidWithExpiry> workingUidsMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (originalUidsMap != null) {
            workingUidsMap.putAll(originalUidsMap);
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

    /***
     * Removes expired {@link Uids}, updates {@link UidsCookie} with new uid for family name according to priority
     */
    public UpdateResult<UidsCookie> updateUidsCookie(UidsCookie uidsCookie, String familyName, String uid) {
        final UidsCookie initialCookie = removeExpiredUids(uidsCookie);

        // At the moment, Facebook calls /setuid with a UID of 0 if the user isn't logged into Facebook.
        // They shouldn't be sending us a sentinel value... but since they are, we're refusing to save that ID.
        if (StringUtils.isBlank(uid) || UidsCookie.isFacebookSentinel(familyName, uid)) {
            return UpdateResult.unaltered(initialCookie);
        }

        final UidsCookie updatedCookie = initialCookie.updateUid(familyName, uid);
        return UpdateResult.updated(updatedCookie);
    }

    private static UidsCookie removeExpiredUids(UidsCookie uidsCookie) {
        final Set<String> families = uidsCookie.getCookieUids().getUids().keySet();

        UidsCookie updatedCookie = uidsCookie;
        for (String family : families) {
            updatedCookie = updatedCookie.hasLiveUidFrom(family)
                    ? updatedCookie
                    : updatedCookie.deleteUid(family);
        }

        return updatedCookie;
    }

    public List<Cookie> splitUidsIntoCookies(UidsCookie uidsCookie) {
        final Uids cookieUids = uidsCookie.getCookieUids();
        final Map<String, UidWithExpiry> uids = cookieUids.getUids();
        final boolean hasOptout = !uidsCookie.allowsSync();

        final Iterator<String> cookieFamilies = cookieFamilyNamesByDescPriorityAndExpiration(uidsCookie);
        final List<Cookie> splitCookies = new ArrayList<>();

        final int cookieSchemaSize = UidsCookieSize.schemaSize(makeCookie(COOKIE_NAME, StringUtils.EMPTY, ttlSeconds));
        String nextCookieFamily = null;
        for (int i = 0; i < numberOfUidCookies; i++) {
            final int digits = i < 10 ? Integer.signum(i) : 2;
            final UidsCookieSize uidsCookieSize = new UidsCookieSize(cookieSchemaSize + digits, maxCookieSizeBytes);

            final Map<String, UidWithExpiry> tempUids = new HashMap<>();
            while (nextCookieFamily != null || cookieFamilies.hasNext()) {
                nextCookieFamily = nextCookieFamily == null ? cookieFamilies.next() : nextCookieFamily;
                final UidWithExpiry uidWithExpiry = uids.get(nextCookieFamily);

                uidsCookieSize.addUid(nextCookieFamily, uidWithExpiry.getUid());
                if (!uidsCookieSize.isValid()) {
                    break;
                }

                tempUids.put(nextCookieFamily, uidWithExpiry);
                nextCookieFamily = null;
            }

            final String uidsName = i == 0 ? COOKIE_NAME : COOKIE_NAME_FORMAT.formatted(i + 1);

            if (tempUids.isEmpty()) {
                splitCookies.add(expiredCookie(uidsName));
            } else {
                splitCookies.add(aliveCookie(
                        uidsName,
                        new UidsCookie(Uids.builder().uids(tempUids).optout(hasOptout).build(), mapper)));
            }
        }

        if (nextCookieFamily != null) {
            updateSyncSizeMetrics(nextCookieFamily);
        }

        cookieFamilies.forEachRemaining(this::updateSyncSizeMetrics);

        return splitCookies;
    }

    private Iterator<String> cookieFamilyNamesByDescPriorityAndExpiration(UidsCookie uidsCookie) {
        return uidsCookie.getCookieUids().getUids().entrySet().stream()
                .sorted(this::compareCookieFamilyNames)
                .map(Map.Entry::getKey)
                .iterator();
    }

    private int compareCookieFamilyNames(Map.Entry<String, UidWithExpiry> left,
                                         Map.Entry<String, UidWithExpiry> right) {

        final boolean leftPrioritized = prioritizedCoopSyncProvider.isPrioritizedFamily(left.getKey());
        final boolean rightPrioritized = prioritizedCoopSyncProvider.isPrioritizedFamily(right.getKey());

        if ((leftPrioritized && rightPrioritized) || (!leftPrioritized && !rightPrioritized)) {
            return left.getValue().getExpires().compareTo(right.getValue().getExpires());
        } else if (leftPrioritized) {
            return -1;
        } else { // right is prioritized
            return 1;
        }
    }

    private void updateSyncSizeMetrics(String nextCookieFamily) {
        if (prioritizedCoopSyncProvider.isPrioritizedFamily(nextCookieFamily)) {
            metrics.updateUserSyncSizedOutMetric(nextCookieFamily);
        } else {
            metrics.updateUserSyncSizeBlockedMetric(nextCookieFamily);
        }
    }

    public String hostCookieUidToSync(RoutingContext routingContext, String cookieFamilyName) {
        if (!StringUtils.equals(cookieFamilyName, hostCookieFamily)) {
            return null;
        }

        final Map<String, String> cookies = HttpUtil.cookiesAsMap(routingContext);
        final String hostCookieUid = parseHostCookie(cookies);
        if (hostCookieUid == null) {
            return null;
        }

        final boolean inSync = Optional.ofNullable(parseUids(cookies))
                .map(Uids::getUids)
                .map(uids -> uids.get(cookieFamilyName))
                .map(UidWithExpiry::getUid)
                .filter(uid -> StringUtils.equals(hostCookieUid, uid))
                .isPresent();

        return inSync ? null : hostCookieUid;
    }
}
