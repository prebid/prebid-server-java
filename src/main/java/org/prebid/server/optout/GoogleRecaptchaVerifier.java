package org.prebid.server.optout;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.optout.model.RecaptchaResponse;
import org.prebid.server.vertx.http.HttpClient;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Objects;

/**
 * Implements the connection to the re-captcha validation endpoint.
 */
public class GoogleRecaptchaVerifier {

    private static final Logger logger = LoggerFactory.getLogger(GoogleRecaptchaVerifier.class);

    private final HttpClient httpClient;
    private final String recaptchaUrl;
    private final String recaptchaSecret;

    public GoogleRecaptchaVerifier(HttpClient httpClient, String recaptchaUrl, String recaptchaSecret) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.recaptchaUrl = Objects.requireNonNull(recaptchaUrl);
        this.recaptchaSecret = Objects.requireNonNull(recaptchaSecret);
    }

    /**
     * Validates reCAPTCHA token by sending it to the re-captcha verfier endpoint url.
     */
    public Future<Void> verify(String recaptcha) {
        final MultiMap headers = MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)
                .add(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON);

        final Future<Void> future = Future.future();
        httpClient.request(HttpMethod.POST, recaptchaUrl, headers, encodedBody(recaptchaSecret, recaptcha), 2000L,
                response -> handleResponse(response, future),
                exception -> handleException(exception, future));
        return future;
    }

    private void handleResponse(HttpClientResponse response, Future<Void> future) {
        response
                .bodyHandler(buffer -> handleResponseAndBody(response.statusCode(), buffer.toString(), future))
                .exceptionHandler(exception -> handleException(exception, future));
    }

    private void handleResponseAndBody(int statusCode, String body, Future<Void> future) {
        if (statusCode != 200) {
            logger.warn("Google recaptcha response code is {0}, body: {1}", statusCode, body);
            future.fail(new PreBidException(String.format("HTTP status code %d", statusCode)));
            return;
        }

        final RecaptchaResponse response;
        try {
            response = Json.decodeValue(body, RecaptchaResponse.class);
        } catch (DecodeException e) {
            future.fail(new PreBidException(String.format("Cannot parse Google recaptcha response: %s", body)));
            return;
        }

        if (Objects.equals(response.getSuccess(), Boolean.TRUE)) {
            future.complete();
        } else {
            final List<String> errorCodes = response.getErrorCodes();
            final String errors = errorCodes != null ? String.join(", ", errorCodes) : null;
            future.fail(new PreBidException(String.format("Google recaptcha verify failed: %s", errors)));
        }
    }

    private void handleException(Throwable exception, Future<Void> future) {
        logger.warn("Error occurred while sending request to verify google recaptcha", exception);
        future.fail(exception);
    }

    private static String encodedBody(String secret, String recaptcha) {
        return "secret=" + encodeValue(secret) + "&response=" + encodeValue(recaptcha);
    }

    private static String encodeValue(String value) {
        try {
            return URLEncoder.encode(value, "utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new PreBidException(String.format("Cannot encode request form value: %s", value), e);
        }
    }
}
