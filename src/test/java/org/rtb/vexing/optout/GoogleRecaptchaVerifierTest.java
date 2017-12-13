package org.rtb.vexing.optout;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.mockito.stubbing.Answer;
import org.rtb.vexing.VertxTest;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.exception.PreBidException;

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
    private HttpClientRequest httpClientRequest;
    @Mock
    private RoutingContext routingContext;
    @Mock
    private HttpServerRequest httpRequest;
    @Mock
    private ApplicationConfig config;

    private GoogleRecaptchaVerifier googleRecaptchaVerifier;

    @Before
    public void setUp() {
        given(httpClient.postAbs(anyString(), any())).willReturn(httpClientRequest);

        given(httpClientRequest.putHeader(any(CharSequence.class), any(CharSequence.class)))
                .willReturn(httpClientRequest);
        given(httpClientRequest.setTimeout(anyLong())).willReturn(httpClientRequest);
        given(httpClientRequest.exceptionHandler(any())).willReturn(httpClientRequest);

        given(routingContext.request()).willReturn(httpRequest);
        given(httpRequest.getFormAttribute("g-recaptcha-response")).willReturn("recaptcha1");

        given(config.getString("recaptcha_url")).willReturn("http://optout/url");
        given(config.getString("recaptcha_secret")).willReturn("abc");

        googleRecaptchaVerifier = GoogleRecaptchaVerifier.create(httpClient, config);
    }

    @Test
    public void creationShouldFailOnNullArguments() {
        // then
        assertThatNullPointerException().isThrownBy(() -> GoogleRecaptchaVerifier.create(null, null));
        assertThatNullPointerException().isThrownBy(() -> GoogleRecaptchaVerifier.create(httpClient, null));
    }

    @Test
    public void shouldFailIfRecaptchaIsMissing() {
        // then
        assertThatNullPointerException().isThrownBy(() -> googleRecaptchaVerifier.verify(null));
    }

    @Test
    public void shouldRequestToGoogleRecaptchaVerifierWithExpectedRequestBody() {
        // when
        googleRecaptchaVerifier.verify("recaptcha1");

        // then
        final String request = captureGoogleRecaptchaRequest();

        assertThat(request).isEqualTo("secret=abc&response=recaptcha1");
    }

    @Test
    public void shouldFailIfHttpRequestFails() {
        // given
        given(httpClientRequest.exceptionHandler(any()))
                .willAnswer(withSelfAndPassObjectToHandler(new RuntimeException("Request exception")));

        // when
        final Future<?> future = googleRecaptchaVerifier.verify("recaptcha1");

        // then
        assertThat(future.failed()).isTrue();
        assertThat(future.cause()).isInstanceOf(RuntimeException.class).hasMessage("Request exception");
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
                .hasMessage("Google recaptcha verify failed: bad-request");
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
        given(httpClient.postAbs(anyString(), any()))
                .willAnswer(withRequestAndPassResponseToHandler(httpClientResponse));
        given(httpClientResponse.statusCode()).willReturn(statusCode);
        return httpClientResponse;
    }

    @SuppressWarnings("unchecked")
    private Answer<Object> withRequestAndPassResponseToHandler(HttpClientResponse httpClientResponse) {
        return inv -> {
            // invoking passed HttpClientResponse handler right away passing mock response to it
            ((Handler<HttpClientResponse>) inv.getArgument(1)).handle(httpClientResponse);
            return httpClientRequest;
        };
    }

    @SuppressWarnings("unchecked")
    private static <T> Answer<Object> withSelfAndPassObjectToHandler(T obj) {
        return inv -> {
            ((Handler<T>) inv.getArgument(0)).handle(obj);
            return inv.getMock();
        };
    }

    private String captureGoogleRecaptchaRequest() {
        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(httpClientRequest).end(captor.capture());
        return captor.getValue();
    }
}
