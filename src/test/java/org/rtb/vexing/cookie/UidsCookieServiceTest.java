package org.rtb.vexing.cookie;

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
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.model.UidWithExpiry;
import org.rtb.vexing.model.Uids;

import java.time.Clock;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class UidsCookieServiceTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String ADNXS = "adnxs";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationConfig config;
    @Mock
    private RoutingContext routingContext;

    private UidsCookieService uidsCookieService;

    @Before
    public void setUp() {
        given(config.getString(eq("host_cookie.domain"), eq(null))).willReturn("cookie-domain");
        given(config.getString(eq("host_cookie.optout_cookie.name"), eq(null))).willReturn("trp_optout");
        given(config.getString(eq("host_cookie.optout_cookie.value"), eq(null))).willReturn("true");

        uidsCookieService = UidsCookieService.create(config);
    }

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> UidsCookieService.create(null));
    }

    @Test
    public void createShouldTolerateMissingConfigParameters() {
        assertThatCode(() -> UidsCookieService.create(config)).doesNotThrowAnyException();
    }

    @Test
    public void shouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> uidsCookieService.parseFromRequest(null));
    }

    @Test
    public void shouldReturnNonEmptyUidsCookie() {
        // given
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ=="));

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
        assertThat(uids.bday).isCloseTo(ZonedDateTime.now(Clock.systemUTC()), within(10, ChronoUnit.SECONDS));
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutTrueIfUidsCookieIsMissingAndOptoutCookieHasExpectedValue() {
        // given
        given(routingContext.getCookie(eq("trp_optout"))).willReturn(Cookie.cookie("trp_optout", "true"));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isFalse();
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutTrueIfUidsCookieIsPresentAndOptoutCookieHasExpectedValue() {
        // given
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ=="));

        given(routingContext.getCookie(eq("trp_optout"))).willReturn(Cookie.cookie("trp_optout", "true"));

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
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ=="));

        given(routingContext.getCookie(eq("trp_optout"))).willReturn(Cookie.cookie("trp_optout", "dummy"));

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
        given(config.getString(eq("host_cookie.optout_cookie.name"), eq(null))).willReturn(null);

        uidsCookieService = UidsCookieService.create(config);

        given(routingContext.getCookie(eq("trp_optout"))).willReturn(Cookie.cookie("trp_optout", "true"));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isTrue();
        verify(routingContext).getCookie(eq("uids"));
        verifyNoMoreInteractions(routingContext);
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutFalseIfOptoutCookieValueNotSpecified() {
        // given
        given(config.getString(eq("host_cookie.optout_cookie.value"), eq(null))).willReturn(null);

        uidsCookieService = UidsCookieService.create(config);

        given(routingContext.getCookie(eq("trp_optout"))).willReturn(Cookie.cookie("trp_optout", "true"));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isTrue();
        verify(routingContext).getCookie(eq("uids"));
        verifyNoMoreInteractions(routingContext);
    }

    @Test
    public void shouldReturnRubiconCookieValueFromHostCookieWhenUidValueIsAbsent() {
        // given
        given(config.getString(eq("host_cookie.family"), eq(null))).willReturn("rubicon");
        given(config.getString(eq("host_cookie.cookie_name"), eq(null))).willReturn("khaos");

        given(routingContext.getCookie(eq("khaos"))).willReturn(Cookie.cookie("khaos",
                "abc123"));

        uidsCookieService = UidsCookieService.create(config);

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        verify(routingContext).getCookie(eq("uids"));
        verify(routingContext).getCookie(eq("khaos"));
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("abc123");
    }

    @Test
    public void shouldReturnRubiconCookieValueFromUidsCookieWhenUidValueIsPresent() {
        // given
        given(config.getString(eq("host_cookie.family"), eq(null))).willReturn("rubicon");
        given(config.getString(eq("host_cookie.cookie_name"), eq(null))).willReturn("khaos");

        given(routingContext.getCookie(eq("khaos"))).willReturn(Cookie.cookie("khaos",
                "abc123"));
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ=="));

        uidsCookieService = UidsCookieService.create(config);

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        verify(routingContext).getCookie(eq("uids"));
        verify(routingContext).getCookie(eq("khaos"));
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
    }

    @Test
    public void shouldSkipFacebookSentinelFromUidsCookie() {
        // given
        final Map<String, UidWithExpiry> uidsWithExpiry = new HashMap<>();
        uidsWithExpiry.put(RUBICON, UidWithExpiry.live("J5VLCWQP-26-CWFT"));
        uidsWithExpiry.put("audienceNetwork", UidWithExpiry.live("0"));
        final Uids uids = Uids.builder().uids(uidsWithExpiry).build();
        final String encodedUids = encodeUids(uids);

        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", encodedUids));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
        assertThat(uidsCookie.uidFrom("audienceNetwork")).isNull();
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
        final Map<String, UidWithExpiry> uids = decodeUids(cookie.getValue()).uids;

        assertThat(uids).hasSize(2);
        assertThat(uids.get(RUBICON).uid).isEqualTo("rubiconUid");
        assertThat(uids.get(RUBICON).expires.toInstant())
                .isCloseTo(Instant.now().plus(14, ChronoUnit.DAYS), within(10, ChronoUnit.SECONDS));

        assertThat(uids.get(ADNXS).uid).isEqualTo("adnxsUid");
        assertThat(uids.get(ADNXS).expires.toInstant())
                .isCloseTo(Instant.now().plus(14, ChronoUnit.DAYS), within(10, ChronoUnit.SECONDS));
    }

    @Test
    public void toCookieShouldReturnCookieWithExpectedExpiration() {
        // when
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(new HashMap<>()).build());
        final Cookie cookie = uidsCookieService.toCookie(uidsCookie);

        // then
        assertThat(cookie.encode()).containsSequence("Max-Age=15552000; Expires=");
    }

    @Test
    public void toCookieShouldReturnCookieWithExpectedDomain() {
        // when
        final UidsCookie uidsCookie = new UidsCookie(Uids.builder().uids(new HashMap<>()).build());
        final Cookie cookie = uidsCookieService.toCookie(uidsCookie);

        // then
        assertThat(cookie.getDomain()).isEqualTo("cookie-domain");
    }

    @Test
    public void shouldCreateUidsFromLegacyUidsIfUidsAreMissed() {
        // given
        // this uids cookie value stands for
        // {"uids":{"rubicon":"J5VLCWQP-26-CWFT"},"tempUIDs":{}},"bday":"2017-08-15T19:47:59.523908376Z"}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIn0sInRlbXBVSURzIjp7fX0sImJkYXkiOiIyMDE3LTA" +
                        "4LTE1VDE5OjQ3OjU5LjUyMzkwODM3NloifQ=="));

        // when
        final UidsCookie uidsCookie = uidsCookieService.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
    }

    @Test
    public void shouldParseHostCookie() {
        // given
        given(config.getString(eq("host_cookie.cookie_name"), isNull())).willReturn("khaos");

        given(routingContext.getCookie(eq("khaos"))).willReturn(Cookie.cookie("khaos", "userId"));

        uidsCookieService = UidsCookieService.create(config);

        // when
        final String hostCookie = uidsCookieService.parseHostCookie(routingContext);

        // then
        assertThat(hostCookie).isEqualTo("userId");
    }

    @Test
    public void shouldNotReadHostCookieIfNameNotSpecified() {
        // when
        final String hostCookie = uidsCookieService.parseHostCookie(routingContext);

        // then
        verifyZeroInteractions(routingContext);
        assertThat(hostCookie).isNull();
    }

    @Test
    public void shouldReturnNullIfHostCookieIsNotPresent() {
        // given
        given(config.getString(eq("host_cookie.cookie_name"), isNull())).willReturn("khaos");

        // this is not necessary but explicitly stated for clarity
        given(routingContext.getCookie(eq("khaos"))).willReturn(null);

        // when
        final String hostCookie = uidsCookieService.parseHostCookie(routingContext);

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
