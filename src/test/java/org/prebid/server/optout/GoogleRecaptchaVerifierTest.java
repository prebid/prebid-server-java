package org.prebid.server.optout;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.vertx.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
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

        googleRecaptchaVerifier = new GoogleRecaptchaVerifier(httpClient, "http://optout/url", "abc");
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        // then
        assertThatNullPointerException().isThrownBy(() -> new GoogleRecaptchaVerifier(null, "http://out/url", "abc"));
        assertThatNullPointerException().isThrownBy(() -> new GoogleRecaptchaVerifier(httpClient, null, "abc"));
        assertThatNullPointerException().isThrownBy(() -> new GoogleRecaptchaVerifier(httpClient, "http://url/", null));
    }

    @Test
    public void shouldRequestToGoogleRecaptchaVerifierWithExpectedRequestBody() {
        // given
        givenHttpClientResponse(200);

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

    private void givenHttpClientProducesException(Throwable throwable) {
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(200);

        given(httpClientResponse.bodyHandler(any())).willReturn(httpClientResponse);
        given(httpClientResponse.exceptionHandler(any())).willAnswer(withSelfAndPassObjectToHandler(throwable));
    }

    private void givenHttpClientReturnsResponse(int statusCode, String response) {
        final HttpClientResponse httpClientResponse = givenHttpClientResponse(statusCode);
        given(httpClientResponse.bodyHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(Buffer.buffer(response)));
    }

    private HttpClientResponse givenHttpClientResponse(int statusCode) {
        final HttpClientResponse httpClientResponse = mock(HttpClientResponse.class);
        given(httpClientResponse.statusCode()).willReturn(statusCode);

        given(httpClient.post(anyString(), any(), any(), anyLong()))
                .willReturn(Future.succeededFuture(httpClientResponse));

        return httpClientResponse;
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T obj) {
        return inv -> {
            // invoking handler right away passing mock to it
            ((Handler<T>) inv.getArgument(0)).handle(obj);
            return inv.getMock();
        };
    }
}
