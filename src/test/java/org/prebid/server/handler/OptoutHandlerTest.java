package org.prebid.server.handler;

import io.netty.util.AsciiString;
import io.vertx.core.Future;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.optout.GoogleRecaptchaVerifier;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class OptoutHandlerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private HttpServerResponse httpResponse;
    @Mock
    private GoogleRecaptchaVerifier googleRecaptchaVerifier;
    @Mock
    private UidsCookieService uidsCookieService;

    private OptoutHandler optoutHandler;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);

        given(httpRequest.getFormAttribute("g-recaptcha-response")).willReturn("recaptcha1");

        given(httpResponse.putHeader(any(CharSequence.class), anyString())).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        given(googleRecaptchaVerifier.verify(anyString())).willReturn(Future.succeededFuture());

        given(uidsCookieService.toCookie(any())).willReturn(Cookie.cookie("cookie", "value"));
        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build(), jacksonMapper));

        optoutHandler = new OptoutHandler(googleRecaptchaVerifier, uidsCookieService,
                OptoutHandler.getOptoutRedirectUrl("http://external/url"), "http://optout/url", "http://optin/url");
    }

    @Test
    public void shouldRedirectIfRecaptchaIsMissing() {
        // given
        given(httpRequest.getFormAttribute("g-recaptcha-response")).willReturn("");
        given(httpRequest.getParam("g-recaptcha-response")).willReturn("");

        // when
        optoutHandler.handle(routingContext);

        // then
        assertThat(captureResponseStatusCode()).isEqualTo(301);
        assertThat(captureResponseLocationHeader()).isEqualTo("http://external/url/static/optout.html");
    }

    @Test
    public void shouldUseRecaptchaPassedInQueryString() {
        // given
        given(httpRequest.getFormAttribute("g-recaptcha-response")).willReturn("");
        given(httpRequest.getParam("g-recaptcha-response")).willReturn("querystring_rcpt");

        // when
        optoutHandler.handle(routingContext);

        // then
        verify(googleRecaptchaVerifier).verify("querystring_rcpt");
    }

    @Test
    public void shouldSkipRecaptchaInQueryStringIfPassedInForm() {
        // given
        given(httpRequest.getFormAttribute("g-recaptcha-response")).willReturn("form_rcpt");
        given(httpRequest.getParam("g-recaptcha-response")).willReturn("querystring_rcpt");

        // when
        optoutHandler.handle(routingContext);

        // then
        verify(googleRecaptchaVerifier).verify("form_rcpt");
    }

    @Test
    public void shouldRespondWithUnauthorized() {
        // given
        given(googleRecaptchaVerifier.verify(anyString())).willReturn(Future.failedFuture(new RuntimeException("RTE")));

        // when
        optoutHandler.handle(routingContext);

        // then
        assertThat(captureResponseStatusCode()).isEqualTo(401);
    }

    @Test
    public void shouldRedirectToOptOutUrlIfOptoutParamIsNotEmpty() {
        // given
        given(googleRecaptchaVerifier.verify(anyString())).willReturn(Future.succeededFuture());
        given(httpRequest.getFormAttribute("optout")).willReturn("1");

        // when
        optoutHandler.handle(routingContext);

        // then
        assertThat(captureResponseStatusCode()).isEqualTo(301);
        assertThat(captureResponseLocationHeader()).isEqualTo("http://optout/url");
    }

    @Test
    public void shouldRedirectToOptInUrlIfOptoutParamIsEmpty() {
        // given
        given(googleRecaptchaVerifier.verify(anyString())).willReturn(Future.succeededFuture());
        given(httpRequest.getFormAttribute("optout")).willReturn("");

        // when
        optoutHandler.handle(routingContext);

        // then
        assertThat(captureResponseStatusCode()).isEqualTo(301);
        assertThat(captureResponseLocationHeader()).isEqualTo("http://optin/url");
    }

    @Test
    public void shouldRedirectToOptInUrlIfOptoutIsMissing() {
        // given
        given(googleRecaptchaVerifier.verify(anyString())).willReturn(Future.succeededFuture());
        given(httpRequest.getFormAttribute("optout")).willReturn(null);

        // when
        optoutHandler.handle(routingContext);

        // then
        assertThat(captureResponseStatusCode()).isEqualTo(301);
        assertThat(captureResponseLocationHeader()).isEqualTo("http://optin/url");
    }

    @Test
    public void getOptoutRedirectUrlShouldReturnExternalUrl() {
        assertThat(OptoutHandler.getOptoutRedirectUrl("http://test.com"))
                .isEqualTo("http://test.com/static/optout.html");
    }

    @Test
    public void getOptoutRedirectUrlFailsOnInvalidUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> OptoutHandler.getOptoutRedirectUrl("test"));
    }

    private Integer captureResponseStatusCode() {
        final ArgumentCaptor<Integer> captor = ArgumentCaptor.forClass(Integer.class);
        verify(httpResponse).setStatusCode(captor.capture());
        return captor.getValue();
    }

    private String captureResponseLocationHeader() {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpResponse).putHeader(eq(new AsciiString("Location")), captor.capture());
        return captor.getValue();
    }
}
