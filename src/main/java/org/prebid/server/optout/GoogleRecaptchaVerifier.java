package org.prebid.server.optout;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
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
     * Validates reCAPTCHA token by sending it to the re-captcha verifier.
     */
    public Future<Void> verify(String recaptcha) {
        return httpClient.post(recaptchaUrl, headers(), encodedBody(recaptchaSecret, recaptcha), 2000L)
                .compose(GoogleRecaptchaVerifier::processResponse)
                .recover(GoogleRecaptchaVerifier::failResponse);
    }

    private static MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_X_WWW_FORM_URLENCODED)
                .add(HttpHeaders.ACCEPT, HttpHeaderValues.APPLICATION_JSON);
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

    private static Future<Void> processResponse(HttpClientResponse response) {
        final Future<Void> future = Future.future();
        response
                .bodyHandler(buffer -> future.complete(
                        processStatusAndBody(response.statusCode(), buffer.toString())))
                .exceptionHandler(future::fail);
        return future;
    }

    private static Void processStatusAndBody(int statusCode, String body) {
        if (statusCode != 200) {
            throw new PreBidException(String.format("HTTP status code %d", statusCode));
        }

        final RecaptchaResponse response;
        try {
            response = Json.decodeValue(body, RecaptchaResponse.class);
        } catch (DecodeException e) {
            throw new PreBidException(String.format("Cannot parse response: %s", body), e);
        }

        if (!Objects.equals(response.getSuccess(), Boolean.TRUE)) {
            final List<String> errorCodes = response.getErrorCodes();
            final String errors = errorCodes != null ? String.join(", ", errorCodes) : null;
            throw new PreBidException(String.format("Verification failed: %s", errors));
        }

        return null;
    }

    private static Future<Void> failResponse(Throwable exception) {
        logger.warn("Error occurred while verifying Google Recaptcha", exception);
        return Future.failedFuture(exception);
    }
}
