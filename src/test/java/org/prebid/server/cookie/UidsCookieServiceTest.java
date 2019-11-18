package org.prebid.server.cookie;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyZeroInteractions;

public class UidsCookieServiceTest extends VertxTest {

    private static final String HOST_COOKIE_DOMAIN = "cookie-domain";
    private static final String OPT_OUT_COOKIE_NAME = "trp_optout";
    private static final String OPT_OUT_COOKIE_VALUE = "true";

    private static final String RUBICON = "rubicon";
    private static final String ADNXS = "adnxs";
    // Zero means size checking is disabled
    private static final int MAX_COOKIE_SIZE_BYTES = 0;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;

    private UidsCookieService uidsCookieService;

    @Before
    public void setUp() {
        uidsCookieService = new UidsCookieService(OPT_OUT_COOKIE_NAME, OPT_OUT_COOKIE_VALUE, null,
                null, HOST_COOKIE_DOMAIN, 90, MAX_COOKIE_SIZE_BYTES);
    }

    @Test
    public void constructorShouldThrowIllegalArgExceptionWhenMaxCookieSizeIsLessThanMin() {
        assertThatThrownBy(() -> new UidsCookieService(null, null, null,
                null, null, 90, 100))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Configured cookie size is less than allowed minimum size of 100");
    }

    @Test
    public void shouldReturnNonEmptyUidsCookieFromCookiesMap() {
        // given
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        final Map<String, String> cookies = singletonMap("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ==");

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromCookies(cookies);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
        assertThat(uidsCookie.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void shouldReturnNonEmptyUidsCookie() {
        // given
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        given(routingContext.cookies()).willReturn(singleton(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ==")));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
        assertThat(uidsCookie.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void shouldReturnNonNullUidsCookieIfUidsCookieIsMissing() {
        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void shouldReturnNonNullUidsCookieIfUidsCookieIsNonBase64() {
        // given
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "abcde"));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void shouldReturnNonNullUidsCookieIfUidsCookieIsNonJson() {
        // given
        // this uids cookie value stands for "abcde"
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "bm9uLWpzb24="));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void shouldReturnNewUidsCookieWithBday() {
        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);
        final String uidsCookieBase64 = uidsCookieService.toCookie(uidsCookie).getValue();

        // then
        final Uids uids = Json.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(uidsCookieBase64)), Uids.class);
        assertThat(uids.getBday()).isCloseTo(ZonedDateTime.now(Clock.systemUTC()), within(10, ChronoUnit.SECONDS));
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutTrueIfUidsCookieIsMissingAndOptoutCookieHasExpectedValue() {
        // given
        given(routingContext.cookies()).willReturn(singleton(Cookie.cookie(OPT_OUT_COOKIE_NAME, OPT_OUT_COOKIE_VALUE)));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isFalse();
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutTrueIfUidsCookieIsPresentAndOptoutCookieHasExpectedValue() {
        // given
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        given(routingContext.cookies()).willReturn(new HashSet<>(asList(
                Cookie.cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ=="),
                Cookie.cookie(OPT_OUT_COOKIE_NAME, OPT_OUT_COOKIE_VALUE))));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isFalse();
        assertThat(uidsCookie.uidFrom(RUBICON)).isNull();
        assertThat(uidsCookie.uidFrom(ADNXS)).isNull();
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutFalseIfOptoutCookieHasNotExpectedValue() {
        // given
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        given(routingContext.cookies()).willReturn(new HashSet<>(asList(
                Cookie.cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ=="),
                Cookie.cookie(OPT_OUT_COOKIE_NAME, "dummy"))));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isTrue();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
        assertThat(uidsCookie.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutFalseIfOptoutCookieNameNotSpecified() {
        // given
        uidsCookieService = new UidsCookieService(null, OPT_OUT_COOKIE_VALUE, null, null,
                HOST_COOKIE_DOMAIN, 90, MAX_COOKIE_SIZE_BYTES);
        given(routingContext.cookies()).willReturn(singleton(Cookie.cookie(OPT_OUT_COOKIE_NAME, OPT_OUT_COOKIE_VALUE)));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isTrue();
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutFalseIfOptoutCookieValueNotSpecified() {
        // given
        uidsCookieService = new UidsCookieService(OPT_OUT_COOKIE_NAME, null, null, null,
                HOST_COOKIE_DOMAIN, 90, MAX_COOKIE_SIZE_BYTES);
        given(routingContext.cookies()).willReturn(singleton(Cookie.cookie(OPT_OUT_COOKIE_NAME, OPT_OUT_COOKIE_VALUE)));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isTrue();
    }

    @Test
    public void shouldReturnRubiconCookieValueFromHostCookieWhenUidValueIsAbsent() {
        // given
        uidsCookieService = new UidsCookieService(OPT_OUT_COOKIE_NAME, OPT_OUT_COOKIE_VALUE, "rubicon",
                "khaos", HOST_COOKIE_DOMAIN, 90, MAX_COOKIE_SIZE_BYTES);
        given(routingContext.cookies()).willReturn(singleton(Cookie.cookie("khaos", "abc123")));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("abc123");
    }

    @Test
    public void shouldReturnRubiconCookieValueFromHostCookieWhenUidValueIsPresentButDiffers() {
        // given
        uidsCookieService = new UidsCookieService(OPT_OUT_COOKIE_NAME, OPT_OUT_COOKIE_VALUE, "rubicon",
                "khaos", HOST_COOKIE_DOMAIN, 90, MAX_COOKIE_SIZE_BYTES);

        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        given(routingContext.cookies()).willReturn(new HashSet<>(asList(
                Cookie.cookie("uids", "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ=="),
                Cookie.cookie("khaos", "abc123"))));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("abc123");
    }

    @Test
    public void shouldSkipFacebookSentinelFromUidsCookie() {
        // given
        final Map<String, UidWithExpiry> uidsWithExpiry = new HashMap<>();
        uidsWithExpiry.put(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"));
        uidsWithExpiry.put("audienceNetwork", UidWithExpiry.live("0"));
        final Uids uids = Uids.builder().uids(uidsWithExpiry).build();
        final String encodedUids = encodeUids(uids);

        given(routingContext.cookies()).willReturn(singleton(Cookie.cookie("uids", encodedUids)));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
        assertThat(uidsCookie.uidFrom("audienceNetwork")).isNull();
    }


    @Test
    public void toCookieShouldEnforceMaxCookieSizeAndRemoveAUidWithCloserExpirationDate() {
        // given
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(new HashMap<>()).build())
                .updateUid(RUBICON, "rubiconUid")
                .updateUid("conversant", "conversantUid")
                .updateUid(ADNXS, "adnxsUid")
                .updateUid("sharethrough", "sharethroughUid")
                .updateUid("improvedigital", "improvedigitalUid")
                .updateUid("somoaudience", "somoaudienceUid")
                .updateUid("verizonmedia", "verizonmediaUid");

        // the size of uidsCookie above is 530, therefore it is expected to be modified.
        final int maxCookieSizeBytes = 500;
        uidsCookieService = new UidsCookieService(OPT_OUT_COOKIE_NAME, OPT_OUT_COOKIE_VALUE, null,
                null, HOST_COOKIE_DOMAIN, 90, maxCookieSizeBytes);

        // when
        final Cookie cookie = uidsCookieService.toCookie(uidsCookie);

        // then
        final Map<String, UidWithExpiry> uids = decodeUids(cookie.getValue()).getUids();

        // 7 UIDs were added above.
        // NOTE: order can be different, therefore unable to check what exact UID is missing
        assertThat(uids).hasSize(6);
    }

    @Test
    public void toCookieShouldReturnCookieWithExpectedValue() {
        // given
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(new HashMap<>()).build())
                .updateUid(RUBICON, "rubiconUid")
                .updateUid(ADNXS, "adnxsUid");

        // when
        final Cookie cookie = uidsCookieService.toCookie(uidsCookie);

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
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(new HashMap<>()).build());
        final Cookie cookie = uidsCookieService.toCookie(uidsCookie);

        // then
        assertThat(cookie.encode()).containsSequence("Max-Age=7776000; Expires=");
    }

    @Test
    public void toCookieShouldReturnCookieWithExpectedDomain() {
        // when
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(new HashMap<>()).build());
        final Cookie cookie = uidsCookieService.toCookie(uidsCookie);

        // then
        assertThat(cookie.getDomain()).isEqualTo(HOST_COOKIE_DOMAIN);
    }

    @Test
    public void shouldCreateUidsFromLegacyUidsIfUidsAreMissed() {
        // given
        // this uids cookie value stands for
        // {"uids":{"rubicon":"J5VLCWQP-26-CWFT"},"tempUIDs":{}},"bday":"2017-08-15T19:47:59.523908376Z"}
        given(routingContext.cookies()).willReturn(singleton(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIn0sInRlbXBVSURzIjp7fX0sImJkYXkiOiIyMDE3LTA" +
                        "4LTE1VDE5OjQ3OjU5LjUyMzkwODM3NloifQ==")));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
    }

    @Test
    public void shouldParseHostCookie() {
        // given
        uidsCookieService = new UidsCookieService(OPT_OUT_COOKIE_NAME, OPT_OUT_COOKIE_VALUE, null,
                "khaos", HOST_COOKIE_DOMAIN, 90, MAX_COOKIE_SIZE_BYTES);

        // when
        final String hostCookie = uidsCookieService.parseHostCookie(singletonMap("khaos", "userId"));

        // then
        assertThat(hostCookie).isEqualTo("userId");
    }

    @Test
    public void shouldNotReadHostCookieIfNameNotSpecified() {
        // when
        final String hostCookie = uidsCookieService.parseHostCookie(emptyMap());

        // then
        verifyZeroInteractions(routingContext);
        assertThat(hostCookie).isNull();
    }

    @Test
    public void shouldReturnNullIfHostCookieIsNotPresent() {
        // when
        final String hostCookie = uidsCookieService.parseHostCookie(singletonMap("khaos", null));

        // then
        assertThat(hostCookie).isNull();
    }

    private static String encodeUids(Uids uids) {
        return Base64.getUrlEncoder().encodeToString(Json.encodeToBuffer(uids).getBytes());
    }

    private static Uids decodeUids(String value) {
        return Json.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(value)), Uids.class);
    }
}
