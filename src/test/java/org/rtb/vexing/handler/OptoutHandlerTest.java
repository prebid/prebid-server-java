package org.rtb.vexing.handler;

import io.netty.util.AsciiString;
import io.vertx.core.Future;
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
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.cookie.UidsCookieService;
import org.rtb.vexing.model.Uids;
import org.rtb.vexing.optout.GoogleRecaptchaVerifier;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
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
    private ApplicationConfig config;
    @Mock
    private GoogleRecaptchaVerifier googleRecaptchaVerifier;
    @Mock
    private UidsCookieService uidsCookieService;

    private OptoutHandler optoutHandler;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(routingContext.response()).willReturn(httpResponse);
        given(routingContext.addCookie(any())).willReturn(routingContext);

        given(httpRequest.getFormAttribute("g-recaptcha-response")).willReturn("recaptcha1");

        given(httpResponse.putHeader(any(CharSequence.class), anyString())).willReturn(httpResponse);
        given(httpResponse.setStatusCode(anyInt())).willReturn(httpResponse);

        given(config.getString("external_url")).willReturn("http://external/url");
        given(config.getString("host_cookie.opt_out_url")).willReturn("http://optout/url");
        given(config.getString("host_cookie.opt_in_url")).willReturn("http://optin/url");

        given(googleRecaptchaVerifier.verify(anyString())).willReturn(Future.succeededFuture());

        given(uidsCookieService.parseFromRequest(any()))
                .willReturn(new UidsCookie(Uids.builder().uids(emptyMap()).build()));

        optoutHandler = OptoutHandler.create(config, googleRecaptchaVerifier, uidsCookieService);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        // then
        assertThatNullPointerException().isThrownBy(
                () -> OptoutHandler.create(null, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> OptoutHandler.create(config, null, null));
        assertThatNullPointerException().isThrownBy(
                () -> OptoutHandler.create(config, googleRecaptchaVerifier, null));
    }

    @Test
    public void creationShouldFailOnInvalidExternalUrlConfig() {
        // given
        given(config.getString("external_url")).willReturn("invalid_url");

        // then
        assertThatIllegalArgumentException().isThrownBy(
                () -> OptoutHandler.create(config, googleRecaptchaVerifier, uidsCookieService));
    }

    @Test
    public void creationShouldFailOnInvalidOptOutlUrlConfig() {
        // given
        given(config.getString("host_cookie.opt_out_url")).willReturn("invalid_url");

        // then
        assertThatIllegalArgumentException().isThrownBy(
                () -> OptoutHandler.create(config, googleRecaptchaVerifier, uidsCookieService));
    }

    @Test
    public void creationShouldFailOnInvalidOptInlUrlConfig() {
        // given
        given(config.getString("host_cookie.opt_in_url")).willReturn("invalid_url");

        // then
        assertThatIllegalArgumentException().isThrownBy(
                () -> OptoutHandler.create(config, googleRecaptchaVerifier, uidsCookieService));
    }

    @Test
    public void shouldFailOnNullArguments() {
        // then
        assertThatNullPointerException().isThrownBy(() -> optoutHandler.optout(null));
    }

    @Test
    public void shouldRedirectIfRecaptchaIsMissing() {
        // given
        given(httpRequest.getFormAttribute("g-recaptcha-response")).willReturn("");
        given(httpRequest.getParam("g-recaptcha-response")).willReturn("");

        // when
        optoutHandler.optout(routingContext);

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
        optoutHandler.optout(routingContext);

        // then
        verify(googleRecaptchaVerifier).verify("querystring_rcpt");
    }

    @Test
    public void shouldSkipRecaptchaInQueryStringIfPassedInForm() {
        // given
        given(httpRequest.getFormAttribute("g-recaptcha-response")).willReturn("form_rcpt");
        given(httpRequest.getParam("g-recaptcha-response")).willReturn("querystring_rcpt");

        // when
        optoutHandler.optout(routingContext);

        // then
        verify(googleRecaptchaVerifier).verify("form_rcpt");
    }

    @Test
    public void shouldRespondWithUnauthorized() {
        // given
        given(googleRecaptchaVerifier.verify(anyString())).willReturn(Future.failedFuture(new RuntimeException("RTE")));

        // when
        optoutHandler.optout(routingContext);

        // then
        assertThat(captureResponseStatusCode()).isEqualTo(401);
    }

    @Test
    public void shouldRedirectToOptOutUrlIfOptoutParamIsNotEmpty() {
        // given
        given(googleRecaptchaVerifier.verify(anyString())).willReturn(Future.succeededFuture());
        given(httpRequest.getFormAttribute("optout")).willReturn("1");

        // when
        optoutHandler.optout(routingContext);

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
        optoutHandler.optout(routingContext);

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
        optoutHandler.optout(routingContext);

        // then
        assertThat(captureResponseStatusCode()).isEqualTo(301);
        assertThat(captureResponseLocationHeader()).isEqualTo("http://optin/url");
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
