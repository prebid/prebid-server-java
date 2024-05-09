package org.prebid.server.optout;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

public class GoogleRecaptchaVerifierTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private HttpClient httpClient;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;

    private GoogleRecaptchaVerifier googleRecaptchaVerifier;

    @Before
    public void setUp() {
        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.getFormAttribute("g-recaptcha-response")).willReturn("recaptcha1");

        googleRecaptchaVerifier = new GoogleRecaptchaVerifier("http://optout/url", "abc", httpClient, jacksonMapper);
    }

    @Test
    public void shouldRequestToGoogleRecaptchaVerifierWithExpectedRequestBody() {
        // given
        givenHttpClientReturnsResponse(200, null);

        // when
        googleRecaptchaVerifier.verify("recaptcha1");

        // then
        verify(httpClient).post(anyString(), any(), eq("secret=abc&response=recaptcha1"), anyLong());
    }

    @Test
    public void shouldFailIfReadingHttpResponseFails() {
        // given
        givenHttpClientProducesException(new RuntimeException("Response exception"));

        // when
        final Future<?> future = googleRecaptchaVerifier.verify("recaptcha1");

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Response exception");
    }

    @Test
    public void shouldFailIfResponseCodeIsNot200() {
        // given
        givenHttpClientReturnsResponse(503, "response");

        // when
        final Future<?> future = googleRecaptchaVerifier.verify("recaptcha1");

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class).hasMessage("HTTP status code 503");
    }

    @Test
    public void shouldFailIfResponseBodyCouldNotBeParsed() {
        // given
        givenHttpClientReturnsResponse(200, "response");

        // when
        final Future<?> future = googleRecaptchaVerifier.verify("recaptcha1");

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(PreBidException.class);
    }

    @Test
    public void shouldFailIfGoogleVerificationFailed() {
        // given
        givenHttpClientReturnsResponse(200, "{\"success\": false, \"error-codes\": [\"bad-request\"]}");

        // when
        final Future<?> future = googleRecaptchaVerifier.verify("recaptcha1");

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class)
                .hasMessage("Verification failed: bad-request");
    }

    @Test
    public void shouldSuccededIfGoogleVerificationOk() {
        // given
        givenHttpClientReturnsResponse(200, "{\"success\": true}");

        // when
        final Future<?> future = googleRecaptchaVerifier.verify("recaptcha1");

        // then
        assertThat(future.succeeded()).isTrue();
    }

    private void givenHttpClientReturnsResponse(int statusCode, String response) {
        final HttpClientResponse httpClientResponse = HttpClientResponse.of(statusCode, null, response);
        given(httpClient.post(anyString(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));
    }

    private void givenHttpClientProducesException(Throwable throwable) {
        given(httpClient.post(anyString(), any(), any(), anyLong()))
                .willReturn(Future.failedFuture(throwable));
    }
}
