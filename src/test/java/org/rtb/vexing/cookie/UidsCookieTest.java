package org.rtb.vexing.cookie;

import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.model.Uids;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class UidsCookieTest {

    private static final String RUBICON = "rubicon";
    private static final String ADNXS = "adnxs";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;

    @Test
    public void parseFromRequestShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> UidsCookie.parseFromRequest(null));
    }

    @Test
    public void parseFromRequestShouldReturnNonEmptyUidsCookie() {
        // given
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ=="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
        assertThat(uidsCookie.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void parseFromRequestShouldReturnNonNullUidsCookieIfCookieIsMissing() {
        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void parseFromRequestShouldReturnNonNullUidsCookieIfCookieIsNonBase64() {
        // given
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "abcde"));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void parseFromRequestShouldReturnNonNullUidsCookieIfCookieIsNonJson() {
        // given
        // this uids cookie value stands for "abcde"
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "bm9uLWpzb24="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void parseFromRequestShouldReturnNewUidsCookieWithBday() {
        // when
        final String uidsCookieBase64 = UidsCookie.parseFromRequest(routingContext).toCookie().getValue();

        // then
        final Uids uids = Json.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(uidsCookieBase64)), Uids.class);
        assertThat(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.nnnnnnnnnXXX").parse(uids.bday, Instant::from))
                .isCloseTo(Instant.now(), within(10, ChronoUnit.SECONDS));
    }

    @Test
    public void uidFromShouldReturnNullIfCookieWithoutUid() {
        // given
        // this uids cookie value stands for {}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "e30="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.uidFrom(RUBICON)).isNull();
    }

    @Test
    public void uidFromShouldReturnNullIfCookieIsMissing() {
        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.uidFrom(RUBICON)).isNull();
    }

    @Test
    public void allowsSyncShouldReturnFalseIfOptoutTrue() {
        // given
        // this uids cookie value stands for {"optout": true}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "eyJvcHRvdXQiOiB0cnVlfQ=="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isFalse();
    }

    @Test
    public void allowsSyncShouldReturnTrueIfOptoutAbsent() {
        // given
        // this uids cookie value stands for {}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "e30="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isTrue();
    }

    @Test
    public void hasLiveUidsShouldReturnFalse() {
        assertThat(UidsCookie.parseFromRequest(routingContext).hasLiveUids()).isFalse();
    }

    @Test
    public void isFacebookSentinelShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> UidsCookie.isFacebookSentinel(null, null));
        assertThatNullPointerException().isThrownBy(() -> UidsCookie.isFacebookSentinel("", null));
    }

    @Test
    public void isFacebookSentinelShouldReturnTrueForAudienceNetworkAndZero() {
        assertThat(UidsCookie.isFacebookSentinel("audienceNetwork", "0")).isTrue();
    }

    @Test
    public void isFacebookSentinelShouldReturnFalseForAudienceNetworkAndNonZero() {
        assertThat(UidsCookie.isFacebookSentinel("audienceNetwork", "id")).isFalse();
    }

    @Test
    public void isFacebookSentinelShouldReturnFalseForNonAudienceNetwork() {
        assertThat(UidsCookie.isFacebookSentinel("rubicon", "0")).isFalse();
    }

    @Test
    public void deleteUidShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> UidsCookie.parseFromRequest(routingContext).deleteUid(null));
    }

    @Test
    public void deleteUidShouldReturnUidsCookieWithUidRemoved() {
        // given
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ=="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext).deleteUid(RUBICON);

        // then
        assertThat(uidsCookie.uidFrom(RUBICON)).isNull();
        assertThat(uidsCookie.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void deleteUidShouldIgnoreMissingUid() {
        // given
        // this uids cookie value stands for {"uids":{"adnxs":"12345"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7ImFkbnhzIjoiMTIzNDUifX0="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext).deleteUid(RUBICON);

        // then
        assertThat(uidsCookie.uidFrom(RUBICON)).isNull();
        assertThat(uidsCookie.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void deleteUidShouldTolerateMissingUidsElementInCookie() {
        // given
        // this uids cookie value stands for {}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "e30="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext).deleteUid(RUBICON);

        // then
        assertThat(uidsCookie.uidFrom(RUBICON)).isNull();
    }

    @Test
    public void deleteUidShouldTolerateMissingCookie() {
        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext).deleteUid(RUBICON);

        // then
        assertThat(uidsCookie.uidFrom(RUBICON)).isNull();
    }

    @Test
    public void updateUidShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(
                () -> UidsCookie.parseFromRequest(routingContext).updateUid(null, null));
        assertThatNullPointerException().isThrownBy(
                () -> UidsCookie.parseFromRequest(routingContext).updateUid("", null));
    }

    @Test
    public void updateUidShouldReturnUidsCookieWithUidReplaced() {
        // given
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ=="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext).updateUid(RUBICON, "updatedUid");

        // then
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("updatedUid");
        assertThat(uidsCookie.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void updateUidShouldReturnUidsCookieWithUidAddedIfUidsElementIsPresentInCookie() {
        // given
        // this uids cookie value stands for {"uids":{"adnxs":"12345"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7ImFkbnhzIjoiMTIzNDUifX0="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext).updateUid(RUBICON, "createdUid");

        // then
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("createdUid");
        assertThat(uidsCookie.uidFrom(ADNXS)).isEqualTo("12345");
    }

    @Test
    public void updateUidShouldReturnUidsCookieWithUidAddedIfUidsElementIsMissingInCookie() {
        // given
        // this uids cookie value stands for {}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "e30="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext).updateUid(RUBICON, "createdUid");

        // then
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("createdUid");
    }

    @Test
    public void updateUidShouldReturnUidsCookieWithUidAddedIfCookieIsMissing() {
        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext).updateUid(RUBICON, "createdUid");

        // then
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("createdUid");
    }

    @Test
    public void toCookieShouldReturnCookieWithExpectedValue() {
        // when
        final Cookie uidsCookie = UidsCookie.parseFromRequest(routingContext)
                .updateUid(RUBICON, "rubiconUid")
                .updateUid(ADNXS, "adnxsUid")
                .toCookie();

        // then
        final Uids uids = Json.decodeValue(Buffer.buffer(Base64.getUrlDecoder().decode(uidsCookie.getValue())),
                Uids.class);
        assertThat(uids.uids).containsOnly(entry(RUBICON, "rubiconUid"), entry(ADNXS, "adnxsUid"));
    }

    @Test
    public void toCookieShouldReturnCookieWithExpectedExpiration() {
        // when
        final Cookie uidsCookie = UidsCookie.parseFromRequest(routingContext).toCookie();

        // then
        assertThat(uidsCookie.encode()).containsSequence("Max-Age=15552000; Expires=");
    }
}
