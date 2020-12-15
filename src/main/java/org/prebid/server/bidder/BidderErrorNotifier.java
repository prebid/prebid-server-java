package org.prebid.server.bidder;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.log.ConditionalLogger;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.Objects;

public class BidderErrorNotifier {

    private static final Logger logger = LoggerFactory.getLogger(BidderErrorNotifier.class);
    private static final ConditionalLogger conditionalLogger = new ConditionalLogger(logger);

    private final int timeoutNotificationTimeoutMs;
    private final boolean logTimeoutNotificationResult;
    private final boolean logTimeoutNotificationFailureOnly;
    private final double logTimeoutNotificationSamplingRate;
    private final HttpClient httpClient;
    private final Metrics metrics;

    public BidderErrorNotifier(int timeoutNotificationTimeoutMs,
                               boolean logTimeoutNotificationResult,
                               boolean logTimeoutNotificationFailureOnly,
                               double logTimeoutNotificationSamplingRate,
                               HttpClient httpClient,
                               Metrics metrics) {

        this.timeoutNotificationTimeoutMs = timeoutNotificationTimeoutMs;
        this.logTimeoutNotificationResult = logTimeoutNotificationResult;
        this.logTimeoutNotificationFailureOnly = logTimeoutNotificationFailureOnly;
        this.logTimeoutNotificationSamplingRate = logTimeoutNotificationSamplingRate;
        this.httpClient = Objects.requireNonNull(httpClient);
        this.metrics = Objects.requireNonNull(metrics);
    }

    public <T> HttpCall<T> processTimeout(HttpCall<T> httpCall, Bidder<T> bidder) {
        final BidderError error = httpCall.getError();

        if (error != null && error.getType() == BidderError.Type.timeout) {
            final HttpRequest<Void> timeoutNotification = bidder.makeTimeoutNotification(httpCall.getRequest());
            if (timeoutNotification != null) {
                httpClient.request(
                        timeoutNotification.getMethod(),
                        timeoutNotification.getUri(),
                        timeoutNotification.getHeaders(),
                        timeoutNotification.getBody(),
                        timeoutNotificationTimeoutMs)
                        .map(response -> handleTimeoutNotificationSuccess(response, timeoutNotification))
                        .otherwise(exception -> handleTimeoutNotificationFailure(exception, timeoutNotification));
            }
        }

        return httpCall;
    }

    private Void handleTimeoutNotificationSuccess(HttpClientResponse response, HttpRequest<Void> timeoutNotification) {
        final boolean isSuccessful = response.getStatusCode() >= 200 && response.getStatusCode() < 300;

        metrics.updateTimeoutNotificationMetric(isSuccessful);

        if (logTimeoutNotificationResult && !(logTimeoutNotificationFailureOnly && isSuccessful)) {
            conditionalLogger.warn(
                    String.format(
                            "Notified bidder about timeout. Status code: %s. Request body: %s",
                            response.getStatusCode(),
                            timeoutNotification.getBody()),
                    logTimeoutNotificationSamplingRate);
        }

        return null;
    }

    private Void handleTimeoutNotificationFailure(Throwable exception, HttpRequest<Void> timeoutNotification) {
        metrics.updateTimeoutNotificationMetric(false);

        if (logTimeoutNotificationResult) {
            conditionalLogger.warn(
                    String.format(
                            "Error occurred while notifying bidder about timeout. Error message: %s. Request body: %s",
                            exception.getMessage(),
                            timeoutNotification.getBody()),
                    logTimeoutNotificationSamplingRate);
        }

        return null;
    }
}
