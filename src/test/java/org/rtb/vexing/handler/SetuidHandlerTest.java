package org.rtb.vexing.handler;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.VertxTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

public class SetuidHandlerTest extends VertxTest {

    private static final String RUBICON = "rubicon";

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private SetuidHandler setuidHandler;

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(routingContext.addCookie(any())).willReturn(routingContext);

        setuidHandler = new SetuidHandler();
    }

    @Test
    public void shouldRespondWithErrorIfOptedOut() {
        // given
        // this uids cookie value stands for {"optout": true}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids", "eyJvcHRvdXQiOiB0cnVlfQ=="));

        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        setuidHandler.setuid(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(401));
        verify(httpResponse).end();
        verifyNoMoreInteractions(httpResponse);
    }

    @Test
    public void shouldRespondWithErrorIfBidderParamIsMissing() {
        // given
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        // when
        setuidHandler.setuid(routingContext);

        // then
        verify(httpResponse).setStatusCode(eq(400));
        verify(httpResponse).end();
        verifyNoMoreInteractions(httpResponse);
    }

    @Test
    public void shouldRemoveUidFromCookieIfMissingInRequest() {
        // given
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ=="));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);

        // when
        setuidHandler.setuid(routingContext);

        // then
        final Cookie uidsCookie = captureCookie();
        verify(httpResponse).end();
        // this uids cookie value stands for {"uids":{"adnxs":"12345"}}
        assertThat(uidsCookie.getValue()).isEqualTo("eyJ1aWRzIjp7ImFkbnhzIjoiMTIzNDUifX0=");
    }

    @Test
    public void shouldIgnoreFacebookSentinel() {
        // given
        // this uids cookie value stands for {"uids":{"audienceNetwork":"facebookUid"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7ImF1ZGllbmNlTmV0d29yayI6ImZhY2Vib29rVWlkIn19"));

        given(httpRequest.getParam("bidder")).willReturn("audienceNetwork");
        given(httpRequest.getParam("uid")).willReturn("0");

        // when
        setuidHandler.setuid(routingContext);

        // then
        final Cookie uidsCookie = captureCookie();
        verify(httpResponse).end();
        // this uids cookie value stands for {"uids":{"audienceNetwork":"facebookUid"}}
        assertThat(uidsCookie.getValue()).isEqualTo("eyJ1aWRzIjp7ImF1ZGllbmNlTmV0d29yayI6ImZhY2Vib29rVWlkIn19");
    }

    @Test
    public void shouldUpdateUidInCookieWithRequestValue() {
        // given
        // this uids cookie value stands for {"uids":{"rubicon":"J5VLCWQP-26-CWFT","adnxs":"12345"}}
        given(routingContext.getCookie(eq("uids"))).willReturn(Cookie.cookie("uids",
                "eyJ1aWRzIjp7InJ1Ymljb24iOiJKNVZMQ1dRUC0yNi1DV0ZUIiwiYWRueHMiOiIxMjM0NSJ9fQ=="));

        given(httpRequest.getParam("bidder")).willReturn(RUBICON);
        given(httpRequest.getParam("uid")).willReturn("updatedUid");

        // when
        setuidHandler.setuid(routingContext);

        // then
        final Cookie uidsCookie = captureCookie();
        verify(httpResponse).end();
        // this uids cookie value stands for {"uids":{"adnxs":"12345","rubicon":"updatedUid"}}
        assertThat(uidsCookie.getValue())
                .isEqualTo("eyJ1aWRzIjp7ImFkbnhzIjoiMTIzNDUiLCJydWJpY29uIjoidXBkYXRlZFVpZCJ9fQ==");
    }

    private Cookie captureCookie() {
        final ArgumentCaptor<Cookie> cookieCaptor = ArgumentCaptor.forClass(Cookie.class);
        verify(routingContext).addCookie(cookieCaptor.capture());
        return cookieCaptor.getValue();
    }
}
