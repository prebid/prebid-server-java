package org.prebid.server.bidder;

import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.metric.Metrics;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.Objects;

public class BidderErrorNotifier {

    private final int timeoutNotificationTimeoutMs;
    private final HttpClient httpClient;
    private final Metrics metrics;

    public BidderErrorNotifier(int timeoutNotificationTimeoutMs,
                               HttpClient httpClient,
                               Metrics metrics) {

        this.timeoutNotificationTimeoutMs = timeoutNotificationTimeoutMs;
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
                        .map(this::handleTimeoutNotificationSuccess)
                        .otherwise(this::handleTimeoutNotificationFailure);
            }
        }

        return httpCall;
    }

    private Void handleTimeoutNotificationSuccess(HttpClientResponse response) {
        if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
            metrics.updateTimeoutNotificationMetric(true);
        } else {
            metrics.updateTimeoutNotificationMetric(false);
        }

        return null;
    }

    private Void handleTimeoutNotificationFailure(Throwable exception) {
        metrics.updateTimeoutNotificationMetric(false);

        return null;
    }
}
