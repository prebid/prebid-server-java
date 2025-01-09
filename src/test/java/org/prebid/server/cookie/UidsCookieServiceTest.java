package org.prebid.server.cookie;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.model.UidsCookieUpdateResult;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.metric.Metrics;

import java.io.IOException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.function.Function.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
public class UidsCookieServiceTest extends VertxTest {

    private static final String HOST_COOKIE_DOMAIN = "cookie-domain";
    private static final String OPT_OUT_COOKIE_NAME = "trp_optout";
    private static final String OPT_OUT_COOKIE_VALUE = "true";

    private static final String RUBICON = "rubicon";
    private static final String ADNXS = "adnxs";
    // Zero means size checking is disabled
    private static final int MAX_COOKIE_SIZE_BYTES = 0;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private PrioritizedCoopSyncProvider prioritizedCoopSyncProvider;
    @Mock
    private Metrics metrics;

    private UidsCookieService target;

    @BeforeEach
    public void setUp() {
        target = new UidsCookieService(
                "trp_optout",
                "true",
                null,
                null,
                "cookie-domain",
                90,
                MAX_COOKIE_SIZE_BYTES,
                1,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);
    }

    @Test
    public void shouldReturnNonEmptyUidsCookieFromCookiesMap() {
        // given
        // this uids cookie value stands for { "tempUIDs":{ "rubicon":{ "uid": "J5VLCWQP-26-CWFT",
        // "expires": "2023-12-05T19:00:05.103329-03:00" }, "adnxs":{ "uid": "12345",
        // "expires": "2023-12-05T19:00:05.103329-03:00" } } }
        final Map<String, String> cookies = singletonMap("uids",
                "eyAidGVtcFVJRHMiOnsgInJ1Ymljb24iOnsgInVpZCI6ICJKNVZMQ1dRUC0yNi1DV0ZUIiwg"
                        + "ImV4cGlyZXMiOiAiMjAyMy0xMi0wNVQxOTowMDowNS4xMDMzMjktMDM6MDAiIH0sICJhZG5"
                        + "4cyI6eyAidWlkIjogIjEyMzQ1IiwgImV4cGlyZXMiOiAiMjAyMy0xMi0wNVQxOTowMDowNS"
                        + "4xMDMzMjktMDM6MDAiIH0gfSB9");

        // when
        final UidsCookie uidsCookie = target.parseFromCookies(cookies);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
        assertThat(uidsCookie.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void shouldReturnNonEmptyUidsCookieFromCookiesMapWhenSeveralUidsCookiesArePresent() {
        // given
        final String uidsCookieValue = """
                {
                  "tempUIDs": {
                    "bidderA": {
                      "uid": "bidder-A-uid",
                      "expires": "2023-12-05T19:00:05.103329-03:00"
                    },
                    "bidderB": {
                      "uid": "bidder-B-uid",
                      "expires": "2023-12-05T19:00:05.103329-03:00"
                    }
                  }
                }
                """;

        final String uids2CookieValue = """
                {
                  "tempUIDs": {
                    "bidderC": {
                      "uid": "bidder-C-uid",
                      "expires": "2023-12-05T19:00:05.103329-03:00"
                    },
                    "bidderD": {
                      "uid": "bidder-D-uid",
                      "expires": "2023-12-05T19:00:05.103329-03:00"
                    }
                  }
                }
                """;

        final String encodedUidsCookie = Base64.getEncoder().encodeToString(uidsCookieValue.getBytes());
        final String encodedUids2Cookie = Base64.getEncoder().encodeToString(uids2CookieValue.getBytes());

        final Map<String, String> cookies = Map.of("uids", encodedUidsCookie, "uids2", encodedUids2Cookie);

        // when
        final UidsCookie uidsCookie = target.parseFromCookies(cookies);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom("bidderA")).isEqualTo("bidder-A-uid");
        assertThat(uidsCookie.uidFrom("bidderB")).isEqualTo("bidder-B-uid");
        assertThat(uidsCookie.uidFrom("bidderC")).isEqualTo("bidder-C-uid");
        assertThat(uidsCookie.uidFrom("bidderD")).isEqualTo("bidder-D-uid");
    }

    @Test
    public void shouldReturnMergedUidsFromCookiesWithOldestUidWhenDuplicatesArePresent() {
        // given
        final String uidsCookieValue = """
                {
                  "tempUIDs": {
                    "bidderA": {
                      "uid": "bidder-A1-uid",
                      "expires": "2023-12-05T19:00:05.103329-03:00"
                    },
                    "bidderB": {
                      "uid": "bidder-B-uid",
                      "expires": "2023-12-05T19:00:05.103329-03:00"
                    }
                  }
                }
                """;

        final String uids2CookieValue = """
                {
                  "tempUIDs": {
                    "bidderA": {
                      "uid": "bidder-A2-uid",
                      "expires": "2024-12-05T19:00:05.103329-03:00"
                    }
                  }
                }
                """;

        final String encodedUidsCookie = Base64.getEncoder().encodeToString(uidsCookieValue.getBytes());
        final String encodedUids2Cookie = Base64.getEncoder().encodeToString(uids2CookieValue.getBytes());

        final Map<String, String> cookies = Map.of("uids", encodedUidsCookie, "uids2", encodedUids2Cookie);

        // when
        final UidsCookie uidsCookie = target.parseFromCookies(cookies);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom("bidderA")).isEqualTo("bidder-A2-uid");
        assertThat(uidsCookie.uidFrom("bidderB")).isEqualTo("bidder-B-uid");
    }

    @Test
    public void shouldReturnNonEmptyUidsCookie() {
        // given
        // this uids cookie value stands for { "tempUIDs":{ "rubicon":{ "uid": "J5VLCWQP-26-CWFT",
        // "expires": "2023-12-05T19:00:05.103329-03:00" }, "adnxs":{ "uid": "12345",
        // "expires": "2023-12-05T19:00:05.103329-03:00" } } }
        given(routingContext.cookieMap()).willReturn(singletonMap("uids", Cookie.cookie(
                "tempUIDs",
                "eyAidGVtcFVJRHMiOnsgInJ1Ymljb24iOnsgInVpZCI6ICJKNVZMQ1dRUC0yNi1DV0ZUIiwg"
                        + "ImV4cGlyZXMiOiAiMjAyMy0xMi0wNVQxOTowMDowNS4xMDMzMjktMDM6MDAiIH0sICJhZG5"
                        + "4cyI6eyAidWlkIjogIjEyMzQ1IiwgImV4cGlyZXMiOiAiMjAyMy0xMi0wNVQxOTowMDowNS"
                        + "4xMDMzMjktMDM6MDAiIH0gfSB9")));

        // when
        final UidsCookie uidsCookie = target.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
        assertThat(uidsCookie.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void shouldReturnNonNullUidsCookieIfUidsCookieIsMissing() {
        // when
        final UidsCookie uidsCookie = target.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void shouldReturnNonNullUidsCookieIfUidsCookieIsNonBase64() {
        // given
        given(routingContext.cookieMap()).willReturn(singletonMap("uids", Cookie.cookie("uids", "abcde")));

        // when
        final UidsCookie uidsCookie = target.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void shouldReturnNonNullUidsCookieIfUidsCookieIsNonJson() {
        // given
        // this uids cookie value stands for "abcde"
        given(routingContext.cookieMap()).willReturn(singletonMap("uids", Cookie.cookie("tempUIDs", "bm9uLWpzb24=")));

        // when
        final UidsCookie uidsCookie = target.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutTrueIfUidsCookieIsMissingAndOptoutCookieHasExpectedValue() {
        // given
        given(routingContext.cookieMap()).willReturn(
                singletonMap(OPT_OUT_COOKIE_NAME, Cookie.cookie(OPT_OUT_COOKIE_NAME, OPT_OUT_COOKIE_VALUE)));

        // when
        final UidsCookie uidsCookie = target.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isFalse();
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutTrueIfUidsCookieIsPresentAndOptoutCookieHasExpectedValue() {
        // given
        final Map<String, Cookie> cookies = new HashMap<>();
        // this uids cookie value stands for { "tempUIDs":{ "rubicon":{ "uid": "J5VLCWQP-26-CWFT",
        // "expires": "2023-12-05T19:00:05.103329-03:00" }, "adnxs":{ "uid": "12345",
        // "expires": "2023-12-05T19:00:05.103329-03:00" } } }
        cookies.put("uids",
                Cookie.cookie("uids", "eyAidGVtcFVJRHMiOnsgInJ1Ymljb24iOnsgInVpZCI6ICJKNVZMQ1dRUC0yNi1DV0"
                        + "ZUIiwgImV4cGlyZXMiOiAiMjAyMy0xMi0wNVQxOTowMDowNS4xMDMzMjktMDM6MDAiIH0sICJhZG5"
                        + "4cyI6eyAidWlkIjogIjEyMzQ1IiwgImV4cGlyZXMiOiAiMjAyMy0xMi0wNVQxOTowMDowNS"
                        + "4xMDMzMjktMDM6MDAiIH0gfSB9"));

        cookies.put(OPT_OUT_COOKIE_NAME, Cookie.cookie(OPT_OUT_COOKIE_NAME, OPT_OUT_COOKIE_VALUE));

        given(routingContext.cookieMap()).willReturn(cookies);

        // when
        final UidsCookie uidsCookie = target.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isFalse();
        assertThat(uidsCookie.uidFrom(RUBICON)).isNull();
        assertThat(uidsCookie.uidFrom(ADNXS)).isNull();
    }

    @Test
    public void toCookieShouldSetSameSiteNone() {
        // given
        final Uids uids = Uids.builder()
                .uids(Map.of(RUBICON, UidWithExpiry.live("test")))
                .build();

        final UidsCookie uidsCookie = new UidsCookie(uids, jacksonMapper);

        // when
        final Cookie cookie = target.toCookie("uids", uidsCookie);

        // then
        assertThat(cookie.getSameSite()).isEqualTo(CookieSameSite.NONE);
    }

    @Test
    public void toCookieShouldSetSecure() {
        // given
        final Uids uids = Uids.builder()
                .uids(Map.of(RUBICON, UidWithExpiry.live("test")))
                .build();

        final UidsCookie uidsCookie = new UidsCookie(uids, jacksonMapper);

        // when
        final Cookie cookie = target.toCookie("uids", uidsCookie);

        // then
        assertThat(cookie.isSecure()).isTrue();
    }

    @Test
    public void toCookieShouldSetPath() {
        // given
        final Uids uids = Uids.builder()
                .uids(Map.of(RUBICON, UidWithExpiry.live("test")))
                .build();

        final UidsCookie uidsCookie = new UidsCookie(uids, jacksonMapper);

        // when
        final Cookie cookie = target.toCookie("uids", uidsCookie);

        // then
        assertThat(cookie.getPath()).isEqualTo("/");
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutFalseIfOptoutCookieHasNotExpectedValue() {
        // given
        final Map<String, Cookie> cookies = new HashMap<>();
        // this uids cookie value stands for { "tempUIDs":{ "rubicon":{ "uid": "J5VLCWQP-26-CWFT",
        // "expires": "2023-12-05T19:00:05.103329-03:00" }, "adnxs":{ "uid": "12345",
        // "expires": "2023-12-05T19:00:05.103329-03:00" } } }
        cookies.put("uids", Cookie.cookie(
                "tempUIDs",
                "eyAidGVtcFVJRHMiOnsgInJ1Ymljb24iOnsgInVpZCI6ICJKNVZMQ1dRUC0yNi1DV0ZUIiwg"
                        + "ImV4cGlyZXMiOiAiMjAyMy0xMi0wNVQxOTowMDowNS4xMDMzMjktMDM6MDAiIH0sICJhZG5"
                        + "4cyI6eyAidWlkIjogIjEyMzQ1IiwgImV4cGlyZXMiOiAiMjAyMy0xMi0wNVQxOTowMDowNS"
                        + "4xMDMzMjktMDM6MDAiIH0gfSB9"));
        cookies.put(OPT_OUT_COOKIE_NAME, Cookie.cookie(OPT_OUT_COOKIE_NAME, "dummy"));

        given(routingContext.cookieMap()).willReturn(cookies);

        // when
        final UidsCookie uidsCookie = target.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isTrue();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
        assertThat(uidsCookie.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutFalseIfOptoutCookieNameNotSpecified() {
        // given
        target = new UidsCookieService(
                null,
                "true",
                null,
                null,
                "cookie-domain",
                90,
                MAX_COOKIE_SIZE_BYTES,
                1,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);
        given(routingContext.cookieMap()).willReturn(
                singletonMap(OPT_OUT_COOKIE_NAME, Cookie.cookie("trp_optout", "true")));

        // when
        final UidsCookie uidsCookie = target.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isTrue();
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutFalseIfOptoutCookieValueNotSpecified() {
        // given
        target = new UidsCookieService(
                "trp_optout",
                null,
                null,
                null,
                "cookie-domain",
                90,
                MAX_COOKIE_SIZE_BYTES,
                1,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);
        given(routingContext.cookieMap()).willReturn(
                singletonMap(OPT_OUT_COOKIE_NAME, Cookie.cookie("trp_optout", "true")));

        // when
        final UidsCookie uidsCookie = target.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isTrue();
    }

    @Test
    public void shouldReturnRubiconCookieValueFromHostCookieWhenUidValueIsAbsent() {
        // given
        target = new UidsCookieService(
                "trp_optout",
                "true",
                "rubicon",
                "khaos",
                "cookie-domain",
                90,
                MAX_COOKIE_SIZE_BYTES,
                1,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);
        given(routingContext.cookieMap()).willReturn(singletonMap("khaos", Cookie.cookie("khaos", "abc123")));

        // when
        final UidsCookie uidsCookie = target.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("abc123");
    }

    @Test
    public void shouldReturnRubiconCookieValueFromHostCookieWhenUidValueIsPresentButDiffers() {
        // given
        target = new UidsCookieService(
                "trp_optout",
                "true",
                "rubicon",
                "khaos",
                "cookie-domain",
                90,
                MAX_COOKIE_SIZE_BYTES,
                1,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);

        final Map<String, Cookie> cookies = new HashMap<>();
        // this uids cookie value stands for { "tempUIDs":{ "rubicon":{ "uid": "J5VLCWQP-26-CWFT",
        // "expires": "2023-12-05T19:00:05.103329-03:00" }, "adnxs":{ "uid": "12345",
        // "expires": "2023-12-05T19:00:05.103329-03:00" } } }
        cookies.put("uids",
                Cookie.cookie("uids", "eyAidGVtcFVJRHMiOnsgInJ1Ymljb24iOnsgInVpZCI6ICJKNVZMQ1dRUC0yNi1DV0"
                        + "ZUIiwgImV4cGlyZXMiOiAiMjAyMy0xMi0wNVQxOTowMDowNS4xMDMzMjktMDM6MDAiIH0sICJhZG5"
                        + "4cyI6eyAidWlkIjogIjEyMzQ1IiwgImV4cGlyZXMiOiAiMjAyMy0xMi0wNVQxOTowMDowNS"
                        + "4xMDMzMjktMDM6MDAiIH0gfSB9"));
        cookies.put("khaos", Cookie.cookie("khaos", "abc123"));

        given(routingContext.cookieMap()).willReturn(cookies);

        // when
        final UidsCookie uidsCookie = target.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("abc123");
    }

    @Test
    public void shouldSkipFacebookSentinelFromUidsCookie() throws JsonProcessingException {
        // given
        final Map<String, UidWithExpiry> uidsWithExpiry = new HashMap<>();
        uidsWithExpiry.put(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"));
        uidsWithExpiry.put("audienceNetwork", UidWithExpiry.live("0"));
        final Uids uids = Uids.builder().uids(uidsWithExpiry).build();
        final String encodedUids = encodeUids(uids);

        given(routingContext.cookieMap()).willReturn(singletonMap("uids", Cookie.cookie("uids", encodedUids)));

        // when
        final UidsCookie uidsCookie = target.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
        assertThat(uidsCookie.uidFrom("audienceNetwork")).isNull();
    }

    @Test
    public void toCookieShouldReturnCookieWithExpectedValue() throws IOException {
        // given
        final UidsCookie uidsCookie = new UidsCookie(
                Uids.builder().uids(new HashMap<>()).build(), jacksonMapper)
                .updateUid(RUBICON, "rubiconUid")
                .updateUid(ADNXS, "adnxsUid");

        // when
        final Cookie cookie = target.toCookie("uids", uidsCookie);

        // then
        final Map<String, UidWithExpiry> uids = decodeUids(cookie.getValue()).getUids();

        assertThat(uids).hasSize(2);
        assertThat(uids.get(RUBICON).getUid()).isEqualTo("rubiconUid");
        assertThat(uids.get(RUBICON).getExpires().toInstant())
                .isCloseTo(Instant.now().plus(14, ChronoUnit.DAYS), within(10, ChronoUnit.SECONDS));

        assertThat(uids.get(ADNXS).getUid()).isEqualTo("adnxsUid");
        assertThat(uids.get(ADNXS).getExpires().toInstant())
                .isCloseTo(Instant.now().plus(14, ChronoUnit.DAYS), within(10, ChronoUnit.SECONDS));
    }

    @Test
    public void toCookieShouldReturnCookieWithExpectedExpiration() {
        // when
        final UidsCookie uidsCookie = new UidsCookie(
                Uids.builder().uids(new HashMap<>()).build(), jacksonMapper);
        final Cookie cookie = target.toCookie("uids", uidsCookie);

        // then
        assertThat(cookie.encode()).containsSequence("Max-Age=7776000; Expires=");
    }

    @Test
    public void toCookieShouldReturnCookieWithExpectedDomain() {
        // when
        final UidsCookie uidsCookie = new UidsCookie(
                Uids.builder().uids(new HashMap<>()).build(), jacksonMapper);
        final Cookie cookie = target.toCookie("uids", uidsCookie);

        // then
        assertThat(cookie.getDomain()).isEqualTo(HOST_COOKIE_DOMAIN);
    }

    @Test
    public void shouldParseHostCookie() {
        // given
        target = new UidsCookieService(
                "trp_optout",
                "true",
                null,
                "khaos",
                "cookie-domain",
                90,
                MAX_COOKIE_SIZE_BYTES,
                1,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);

        // when
        final String hostCookie = target.parseHostCookie(singletonMap("khaos", "userId"));

        // then
        assertThat(hostCookie).isEqualTo("userId");
    }

    @Test
    public void shouldNotReadHostCookieIfNameNotSpecified() {
        // when
        final String hostCookie = target.parseHostCookie(emptyMap());

        // then
        verifyNoInteractions(routingContext);
        assertThat(hostCookie).isNull();
    }

    @Test
    public void shouldReturnNullIfHostCookieIsNotPresent() {
        // when
        final String hostCookie = target.parseHostCookie(singletonMap("khaos", null));

        // then
        assertThat(hostCookie).isNull();
    }

    @Test
    public void hostCookieUidToSyncShouldReturnNullWhenCookieFamilyNameDiffersFromHostCookieFamily() {
        // given
        target = new UidsCookieService(
                "trp_optout",
                "true",
                RUBICON,
                "khaos",
                "cookie-domain",
                90,
                MAX_COOKIE_SIZE_BYTES,
                1,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);

        // when
        final String result = target.hostCookieUidToSync(routingContext, "cookie-family");

        // then
        assertThat(result).isNull();
    }

    @Test
    public void hostCookieUidToSyncShouldReturnHostCookieUidWhenHostCookieUidIsAbsent() {
        // given
        target = new UidsCookieService(
                "trp_optout",
                "true",
                RUBICON,
                "khaos",
                "cookie-domain",
                90,
                MAX_COOKIE_SIZE_BYTES,
                1,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);

        final UidsCookie uidsCookie = new UidsCookie(
                Uids.builder().uids(Map.of("cookie-family", UidWithExpiry.live("hostCookieUid"))).build(),
                jacksonMapper);
        final String uidsCookieBase64 = Base64.getUrlEncoder().encodeToString(uidsCookie.toJson().getBytes());

        final Map<String, Cookie> cookieMap = Map.of(
                "khaos", Cookie.cookie("khaos", "hostCookieUid"),
                "uids", Cookie.cookie("uids", uidsCookieBase64));

        given(routingContext.cookieMap()).willReturn(cookieMap);

        // when
        final String result = target.hostCookieUidToSync(routingContext, RUBICON);

        // then
        assertThat(result).isEqualTo("hostCookieUid");
    }

    @Test
    public void hostCookieUidToSyncShouldReturnNullWhenUidsCookieHasNoUidForHostCookieFamily() {
        // given
        target = new UidsCookieService(
                "trp_optout",
                "true",
                RUBICON,
                "khaos",
                "cookie-domain",
                90,
                MAX_COOKIE_SIZE_BYTES,
                1,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);

        given(routingContext.cookieMap()).willReturn(emptyMap());

        // when
        final String result = target.hostCookieUidToSync(routingContext, RUBICON);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void hostCookieUidToSyncShouldReturnNullWhenUidInUidsCookieSameAsUidInHostCookie() {
        // given
        target = new UidsCookieService(
                "trp_optout",
                "true",
                RUBICON,
                "khaos",
                "cookie-domain",
                90,
                MAX_COOKIE_SIZE_BYTES,
                1,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);

        final UidsCookie uidsCookie = new UidsCookie(
                Uids.builder().uids(Map.of(RUBICON, UidWithExpiry.live("hostCookieUid"))).build(),
                jacksonMapper);
        final String uidsCookieBase64 = Base64.getUrlEncoder().encodeToString(uidsCookie.toJson().getBytes());

        final Map<String, Cookie> cookieMap = Map.of(
                "khaos", Cookie.cookie("khaos", "hostCookieUid"),
                "uids", Cookie.cookie("uids", uidsCookieBase64));

        given(routingContext.cookieMap()).willReturn(cookieMap);

        // when
        final String result = target.hostCookieUidToSync(routingContext, RUBICON);

        // then
        assertThat(result).isNull();
    }

    @Test
    public void updateUidsCookieShouldRemoveAllExpiredUids() {
        // given
        final UidsCookie uidsCookie = givenUidsCookie(
                Map.of("family1", UidWithExpiry.expired("uid1"),
                        "family2", UidWithExpiry.live("uid2"),
                        "family3", UidWithExpiry.expired("uid3")));

        // when
        final UidsCookieUpdateResult result = target.updateUidsCookie(uidsCookie, "family4", "uid4");

        // then
        assertThat(result.isSuccessfullyUpdated()).isTrue();

        final Map<String, UidsCookie> actualUidsCookies = result.getUidsCookies();
        assertThat(actualUidsCookies.keySet()).containsOnly("uids");
        assertThat(actualUidsCookies.get("uids"))
                .extracting(UidsCookie::getCookieUids)
                .extracting(Uids::getUids)
                .extracting(Map::values)
                .extracting(ArrayList::new)
                .asList()
                .extracting(object -> (UidWithExpiry) object)
                .extracting(UidWithExpiry::getExpires)
                .allMatch(ZonedDateTime.now()::isBefore);
    }

    @Test
    public void updateUidsCookieShouldNotAddIncomingCookieFamilyWhenItHasBlankUid() {
        // given
        final UidsCookie uidsCookie = givenUidsCookie(
                Map.of("family1", UidWithExpiry.expired("uid1"),
                        "family2", UidWithExpiry.live("uid2"),
                        "family3", UidWithExpiry.expired("uid3")));

        // when
        final UidsCookieUpdateResult result = target.updateUidsCookie(uidsCookie, "family", null);

        // then
        assertThat(result.isSuccessfullyUpdated()).isFalse();
        assertThat(result.getUidsCookies().get("uids").getCookieUids().getUids().keySet()).containsOnly("family2");
    }

    @Test
    public void updateUidsCookieShouldNotAddIncomingCookieFamilyWhenItIsFacebookSentinel() {
        // given
        final UidsCookie uidsCookie = givenUidsCookie(
                Map.of("family1", UidWithExpiry.expired("uid1"),
                        "family2", UidWithExpiry.live("uid2"),
                        "family3", UidWithExpiry.expired("uid3")));

        // when
        final UidsCookieUpdateResult result = target.updateUidsCookie(
                uidsCookie, "audienceNetwork", "0");

        // then
        assertThat(result.isSuccessfullyUpdated()).isFalse();
        assertThat(result.getUidsCookies().get("uids").getCookieUids().getUids().keySet()).containsOnly("family2");
    }

    @Test
    public void updateUidsCookieShouldUpdateCookieAndNotSplitCookieWhenLimitIsNotExceeded() {
        // given
        target = new UidsCookieService(
                "trp_optout",
                "true",
                null,
                null,
                "cookie-domain",
                90,
                MAX_COOKIE_SIZE_BYTES,
                2,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);

        final UidsCookie uidsCookie = givenUidsCookie(Map.of("family", UidWithExpiry.live("uid")));

        // when
        final UidsCookieUpdateResult result = target.updateUidsCookie(
                uidsCookie, "another-family", "uid");

        // then
        assertThat(result.isSuccessfullyUpdated()).isTrue();

        final Map<String, UidsCookie> actualUidsCookies = result.getUidsCookies();
        assertThat(actualUidsCookies.keySet()).containsOnly("uids", "uids2");
        assertThat(actualUidsCookies.get("uids").getCookieUids().getUids().keySet())
                .containsExactly("another-family", "family");
    }

    @Test
    public void updateUidsCookieShouldNotFitNonPrioritizedFamilyWhenSizeExceedsLimitAndLogMetric() {
        // given
        target = new UidsCookieService(
                "trp_optout",
                "true",
                RUBICON,
                "khaos",
                "cookie-domain",
                90,
                500,
                1,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);
        given(prioritizedCoopSyncProvider.isPrioritizedFamily("family")).willReturn(false);
        given(prioritizedCoopSyncProvider.isPrioritizedFamily("very-very-very-very-long-family")).willReturn(true);
        given(prioritizedCoopSyncProvider.isPrioritizedFamily("another-very-very-very-long-family")).willReturn(true);

        // cookie of encoded size 450 bytes
        final UidsCookie uidsCookie = givenUidsCookie(Map.of(
                "very-very-very-very-long-family", UidWithExpiry.live("some-very-very-very-long-uid"),
                "another-very-very-very-long-family", UidWithExpiry.live("another-very-very-very-long-uid")));

        // when
        final UidsCookieUpdateResult result = target.updateUidsCookie(
                uidsCookie, "family", "uid");

        // then
        verify(metrics).updateUserSyncSizeBlockedMetric("family");
        assertThat(result.isSuccessfullyUpdated()).isTrue();

        final Map<String, UidsCookie> actualUidsCookies = result.getUidsCookies();
        assertThat(actualUidsCookies.keySet()).containsOnly("uids");
        assertThat(actualUidsCookies.get("uids").getCookieUids().getUids().keySet())
                .containsExactly("very-very-very-very-long-family", "another-very-very-very-long-family");
    }

    @Test
    public void updateUidsCookieShouldNotFitPrioritizedFamilyWhenSizeExceedsLimitAndIncrementMetric() {
        // given
        target = new UidsCookieService(
                "trp_optout",
                "true",
                RUBICON,
                "khaos",
                "cookie-domain",
                90,
                500,
                1,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);
        given(prioritizedCoopSyncProvider.isPrioritizedFamily(any())).willReturn(true);

        // cookie of encoded size 450 bytes
        final UidsCookie uidsCookie = givenUidsCookie(Map.of(
                "very-very-very-very-long-family", UidWithExpiry.live("some-very-very-very-long-uid"),
                "another-very-very-very-long-family", UidWithExpiry.live("another-very-very-very-long-uid")));

        // when
        final UidsCookieUpdateResult result = target.updateUidsCookie(
                uidsCookie, "family", "uid");

        // then
        verify(metrics).updateUserSyncSizedOutMetric("family");
        assertThat(result.isSuccessfullyUpdated()).isTrue();

        final Map<String, UidsCookie> actualUidsCookies = result.getUidsCookies();
        assertThat(actualUidsCookies.keySet()).containsOnly("uids");
        assertThat(actualUidsCookies.get("uids").getCookieUids().getUids().keySet())
                .containsExactly("very-very-very-very-long-family", "another-very-very-very-long-family");
    }

    @Test
    public void updateUidsCookieShouldFitPrioritizedFamily() {
        // given
        target = new UidsCookieService(
                "trp_optout",
                "true",
                RUBICON,
                "khaos",
                "cookie-domain",
                90,
                500,
                2,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);
        given(prioritizedCoopSyncProvider.isPrioritizedFamily(any())).willReturn(true);

        // cookie of encoded size 450 bytes
        final UidsCookie uidsCookie = givenUidsCookie(Map.of(
                "very-very-very-very-long-family", UidWithExpiry.live("some-very-very-very-long-uid"),
                "another-very-very-very-long-family", UidWithExpiry.live("another-very-very-very-long-uid")));

        // when
        final UidsCookieUpdateResult result = target.updateUidsCookie(
                uidsCookie, "family", "uid");

        // then
        verifyNoInteractions(metrics);
        assertThat(result.isSuccessfullyUpdated()).isTrue();

        final Map<String, UidsCookie> actualUidsCookies = result.getUidsCookies();
        assertThat(actualUidsCookies.keySet()).containsOnly("uids", "uids2");
        assertThat(actualUidsCookies.get("uids"))
                .extracting(UidsCookie::getCookieUids)
                .extracting(Uids::getUids)
                .extracting(Map::keySet)
                .extracting(ArrayList::new)
                .asList()
                .flatExtracting(identity())
                .containsExactly("very-very-very-very-long-family", "another-very-very-very-long-family");

        assertThat(actualUidsCookies.get("uids2"))
                .extracting(UidsCookie::getCookieUids)
                .extracting(Uids::getUids)
                .extracting(Map::keySet)
                .extracting(ArrayList::new)
                .asList()
                .flatExtracting(identity())
                .containsExactly("family");
    }

    @Test
    public void updateUidsCookieShouldFitNonPrioritizedFamily() {
        // given
        target = new UidsCookieService(
                "trp_optout",
                "true",
                RUBICON,
                "khaos",
                "cookie-domain",
                90,
                500,
                5,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);
        given(prioritizedCoopSyncProvider.isPrioritizedFamily(any())).willReturn(false);

        // cookie of encoded size 450 bytes
        final UidsCookie uidsCookie = givenUidsCookie(Map.of(
                "very-very-very-very-long-family", UidWithExpiry.live("some-very-very-very-long-uid"),
                "another-very-very-very-long-family", UidWithExpiry.live("another-very-very-very-long-uid")));

        // when
        final UidsCookieUpdateResult result = target.updateUidsCookie(
                uidsCookie, "family", "uid");

        // then
        verifyNoInteractions(metrics);
        assertThat(result.isSuccessfullyUpdated()).isTrue();

        final Map<String, UidsCookie> actualUidsCookies = result.getUidsCookies();
        assertThat(actualUidsCookies.keySet()).containsOnly("uids", "uids2", "uids3", "uids4", "uids5");
        assertThat(actualUidsCookies.get("uids").getCookieUids().getUids().keySet())
                .containsExactly("very-very-very-very-long-family", "another-very-very-very-long-family");
        assertThat(actualUidsCookies.get("uids2").getCookieUids().getUids().keySet())
                .containsOnly("family");
        assertThat(actualUidsCookies.get("uids3").getCookieUids().getUids()).isEmpty();
        assertThat(actualUidsCookies.get("uids4").getCookieUids().getUids()).isEmpty();
        assertThat(actualUidsCookies.get("uids5").getCookieUids().getUids()).isEmpty();
    }

    @Test
    public void updateUidsCookieShouldDisallowSyncForAllCookiesWhenOptoutSetTrue() {
        // given
        target = new UidsCookieService(
                "trp_optout",
                "true",
                RUBICON,
                "khaos",
                "cookie-domain",
                90,
                500,
                2,
                prioritizedCoopSyncProvider,
                metrics,
                jacksonMapper);
        given(prioritizedCoopSyncProvider.isPrioritizedFamily(any())).willReturn(false);

        // cookie of encoded size 450 bytes
        final Map<String, UidWithExpiry> givenUids = Map.of(
                "very-very-very-very-long-family", UidWithExpiry.live("some-very-very-very-long-uid"),
                "another-very-very-very-long-family", UidWithExpiry.live("another-very-very-very-long-uid"));

        final UidsCookie uidsCookie = new UidsCookie(
                Uids.builder().uids(givenUids).optout(true).build(), jacksonMapper);

        // when
        final UidsCookieUpdateResult result = target.updateUidsCookie(
                uidsCookie, "family", "uid");

        // then
        verifyNoInteractions(metrics);
        assertThat(result.isSuccessfullyUpdated()).isTrue();

        final Map<String, UidsCookie> actualUidsCookies = result.getUidsCookies();
        assertThat(actualUidsCookies.keySet()).containsOnly("uids", "uids2");
        assertThat(actualUidsCookies.get("uids").allowsSync()).isFalse();
        assertThat(actualUidsCookies.get("uids2").allowsSync()).isFalse();
    }

    private UidsCookie givenUidsCookie(Map<String, UidWithExpiry> uids) {
        return new UidsCookie(Uids.builder().uids(uids).build(), jacksonMapper);
    }

    private static String encodeUids(Uids uids) throws JsonProcessingException {
        return Base64.getUrlEncoder().encodeToString(mapper.writeValueAsBytes(uids));
    }

    private static Uids decodeUids(String value) throws IOException {
        return mapper.readValue(Base64.getUrlDecoder().decode(value), Uids.class);
    }
}
