package org.rtb.vexing.cookie;

import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class UidsCookieTest {

    private static final String RUBICON = "rubicon";
    private static final String APPNEXUS = "appnexus";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;

    @Test
    public void shouldParseUidsCookie() {
        // given
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","appnexus":"12345"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYXBwbmV4dXMiOiIxMjM0NSJ9fQ=="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
        assertThat(uidsCookie.uidFrom(RUBICON)).isEqualTo("J5VLCWQP-26-CWFT");
        assertThat(uidsCookie.uidFrom(APPNEXUS)).isEqualTo("12345");
    }

    @Test
    public void shouldTolerateAbsentUidsCookie() {
        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void shouldTolerateNonBase64UidsCookie() {
        // given
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "abcde"));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void shouldTolerateNonJsonUidsCookie() {
        // given
        // this uids cookie value stands for "abcde"
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "bm9uLWpzb24="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie).isNotNull();
    }

    @Test
    public void shouldNotAllowSyncIfOptoutTrue() {
        // given
        // this uids cookie value stands for {"optout": true}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "eyJvcHRvdXQiOiB0cnVlfQ=="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isFalse();
    }

    @Test
    public void shouldAllowSyncIfOptoutAbsent() {
        // given
        // this uids cookie value stands for {}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "e30="));

        // when
        final UidsCookie uidsCookie = UidsCookie.parseFromRequest(routingContext);

        // then
        assertThat(uidsCookie.allowsSync()).isTrue();
    }
}
