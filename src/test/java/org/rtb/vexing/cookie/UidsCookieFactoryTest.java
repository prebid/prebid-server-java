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
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.model.Uids;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

public class UidsCookieFactoryTest {

    private static final String RUBICON = "rubicon";
    private static final String ADNXS = "adnxs";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationConfig config;
    @Mock
    private RoutingContext routingContext;

    private UidsCookieFactory uidsCookieFactory;

    @Before
    public void setUp() {
        given(config.getString(eq("host_cookie.optout_cookie.name"), eq(null))).willReturn("trp_optout");
        given(config.getString(eq("host_cookie.optout_cookie.value"), eq(null))).willReturn("true");

        uidsCookieFactory = UidsCookieFactory.create(config);
    }

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> UidsCookieFactory.create(null));
    }

    @Test
    public void createShouldTolerateMissingConfigParameters() {
        assertThatCode(() -> UidsCookieFactory.create(config)).doesNotThrowAnyException();
    }

    @Test
    public void shouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> uidsCookieFactory.parseFromRequest(null));
    }

    @Test
    public void shouldReturnNonEmptyUidsCookie() {
        // given
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ=="));

        // when
        final UidsCookie uidsCookie = uidsCookieFactory.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
        assertThat(uidsCookie.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void shouldReturnNonNullUidsCookieIfUidsCookieIsMissing() {
        // when
        final UidsCookie uidsCookie = uidsCookieFactory.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void shouldReturnNonNullUidsCookieIfUidsCookieIsNonBase64() {
        // given
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "abcde"));

        // when
        final UidsCookie uidsCookie = uidsCookieFactory.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void shouldReturnNonNullUidsCookieIfUidsCookieIsNonJson() {
        // given
        // this uids cookie value stands for "abcde"
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "bm9uLWpzb24="));

        // when
        final UidsCookie uidsCookie = uidsCookieFactory.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void shouldReturnNewUidsCookieWithBday() {
        // when
        final String uidsCookieBase64 = uidsCookieFactory.parseFromRequest(routingContext).toCookie().getValue();

        // then
        final Uids uids = Json.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(uidsCookieBase64)), Uids.class);
        assertThat(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.nnnnnnnnnXXX").parse(uids.bday, Instant::from))
                .isCloseTo(Instant.now(), within(10, ChronoUnit.SECONDS));
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutTrueIfUidsCookieIsMissingAndOptoutCookieHasExpectedValue() {
        // given
        given(routingContext.getCookie(eq("trp_optout"))).willReturn(Cookie.cookie("trp_optout", "true"));

        // when
        final UidsCookie uidsCookie = uidsCookieFactory.parseFromRequest(routingContext);

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
        final UidsCookie uidsCookie = uidsCookieFactory.parseFromRequest(routingContext);

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
        final UidsCookie uidsCookie = uidsCookieFactory.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isTrue();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
        assertThat(uidsCookie.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutFalseIfOptoutCookieNameNotSpecified() {
        // given
        given(config.getString(eq("host_cookie.optout_cookie.name"), eq(null))).willReturn(null);

        uidsCookieFactory = UidsCookieFactory.create(config);

        given(routingContext.getCookie(eq("trp_optout"))).willReturn(Cookie.cookie("trp_optout", "true"));

        // when
        final UidsCookie uidsCookie = uidsCookieFactory.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isTrue();
        verify(routingContext).getCookie(eq("uids"));
        verifyNoMoreInteractions(routingContext);
    }

    @Test
    public void shouldReturnUidsCookieWithOptoutFalseIfOptoutCookieValueNotSpecified() {
        // given
        given(config.getString(eq("host_cookie.optout_cookie.value"), eq(null))).willReturn(null);

        uidsCookieFactory = UidsCookieFactory.create(config);

        given(routingContext.getCookie(eq("trp_optout"))).willReturn(Cookie.cookie("trp_optout", "true"));

        // when
        final UidsCookie uidsCookie = uidsCookieFactory.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isTrue();
        verify(routingContext).getCookie(eq("uids"));
        verifyNoMoreInteractions(routingContext);
    }

    @Test
    public void shouldReturnRubiconCookieValueFromHostCookieWhenUidValueIsAbsent(){
        //given
        given(config.getString(eq("host_cookie.family"), eq(null))).willReturn("rubicon");
        given(config.getString(eq("host_cookie.cookie_name"), eq(null))).willReturn("khaos");

        given(routingContext.getCookie(eq("khaos"))).willReturn(Cookie.cookie("khaos",
                "abc123"));

        uidsCookieFactory = UidsCookieFactory.create(config);

        // when
        final UidsCookie uidsCookie = uidsCookieFactory.parseFromRequest(routingContext);

        // then
        verify(routingContext).getCookie(eq("uids"));
        verify(routingContext).getCookie(eq("khaos"));
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("abc123");
    }

    @Test
    public void shouldReturnRubiconCookieValueFromUidsCookieWhenUidValueIsPresent(){
        //given
        given(config.getString(eq("host_cookie.family"), eq(null))).willReturn("rubicon");
        given(config.getString(eq("host_cookie.cookie_name"), eq(null))).willReturn("khaos");

        given(routingContext.getCookie(eq("khaos"))).willReturn(Cookie.cookie("khaos",
                "abc123"));
        // given
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ=="));

        uidsCookieFactory = UidsCookieFactory.create(config);

        // when
        final UidsCookie uidsCookie = uidsCookieFactory.parseFromRequest(routingContext);
        // then
        verify(routingContext).getCookie(eq("uids"));
        verify(routingContext).getCookie(eq("khaos"));
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
    }

    @Test
    public void shouldCreateUidsFromLegacyUidsIfUidsAreMissed(){
        // given
        // this uids cookie value stands for
        // {"uids":{"rubicon":"J5VLCWQP-26-CWFT"},"tempUIDs":{}},"bday":"2017-08-15T19:47:59.523908376Z"}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIn0sInRlbXBVSURzIjp7fX0sImJkYXkiOiIyMDE3LTA" +
                        "4LTE1VDE5OjQ3OjU5LjUyMzkwODM3NloifQ=="));

        // when
        final UidsCookie uidsCookie = uidsCookieFactory.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
    }
}
