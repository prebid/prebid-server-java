package org.prebid.server.bidder;

import com.iab.openrtb.request.BidRequest;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.execution.Timeout;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implements HTTP communication functionality common for {@link Bidder}'s.
 * <p>
 * This class exists to help segregate core auction logic and minimize code duplication across the {@link Bidder}
 * implementations.
 * <p>
 * Any logic which can be done within a single Seat goes inside this class.
 * Any logic which requires responses from all Seats goes inside the {@link ExchangeService}.
 */
public class HttpBidderRequester {

    private static final Logger logger = LoggerFactory.getLogger(HttpBidderRequester.class);

    private final HttpClient httpClient;
    private final BidderRequestCompletionTrackerFactory completionTrackerFactory;

    public HttpBidderRequester(HttpClient httpClient,
                               BidderRequestCompletionTrackerFactory completionTrackerFactory) {

        this.httpClient = Objects.requireNonNull(httpClient);
        this.completionTrackerFactory = completionTrackerFactoryOrFallback(completionTrackerFactory);
    }

    /**
     * Executes given request to a given bidder.
     */
    public <T> Future<BidderSeatBid> requestBids(
            Bidder<T> bidder, BidRequest bidRequest, Timeout timeout, boolean debugEnabled) {

        final Result<List<HttpRequest<T>>> httpRequestsWithErrors = bidder.makeHttpRequests(bidRequest);

        final List<BidderError> bidderErrors = httpRequestsWithErrors.getErrors();
        final List<HttpRequest<T>> httpRequests = httpRequestsWithErrors.getValue();

        if (CollectionUtils.isEmpty(httpRequests)) {
            return emptyBidderSeatBidWithErrors(bidderErrors);
        }

        final BidderRequestCompletionTracker completionTracker = completionTrackerFactory.create(bidRequest);

        final ResultBuilder<T> resultBuilder = new ResultBuilder<>(httpRequests, bidderErrors, completionTracker);

        final List<Future<Void>> httpRequestFutures = httpRequests.stream()
                .map(httpRequest -> doRequest(httpRequest, timeout))
                .map(httpCallFuture -> httpCallFuture
                        .map(httpCall -> processHttpCall(bidder, bidRequest, resultBuilder, httpCall)))
                .collect(Collectors.toList());

        final CompositeFuture completionFuture = CompositeFuture.any(
                CompositeFuture.join(new ArrayList<>(httpRequestFutures)),
                completionTracker.future());

        return completionFuture
                .map(ignored -> resultBuilder.toBidderSeatBid(debugEnabled));
    }

    /**
     * Creates {@link Future<BidderSeatBid>} with empty list of {@link BidderBid}s
     * and list of {@link ExtHttpCall}s with list of {@link BidderError}s.
     * If errors list is empty, creates error which indicates of bidder unexpected behaviour.
     */
    private Future<BidderSeatBid> emptyBidderSeatBidWithErrors(List<BidderError> bidderErrors) {
        return Future.succeededFuture(
                BidderSeatBid.of(Collections.emptyList(), Collections.emptyList(), bidderErrors.isEmpty()
                        ? Collections.singletonList(BidderError.failedToRequestBids(
                        "The bidder failed to generate any bid requests, but also failed to generate an error"))
                        : bidderErrors));
    }

    /**
     * Makes an HTTP request and returns {@link Future} that will be eventually completed with success or error result.
     */
    private <T> Future<HttpCall<T>> doRequest(HttpRequest<T> httpRequest, Timeout timeout) {
        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return failResponse(new TimeoutException("Timeout has been exceeded"), httpRequest);
        }

        return httpClient.request(httpRequest.getMethod(), httpRequest.getUri(), httpRequest.getHeaders(),
                httpRequest.getBody(), remainingTimeout)
                .compose(response -> processResponse(response, httpRequest))
                .recover(exception -> failResponse(exception, httpRequest));
    }

    /**
     * Produces {@link Future} with {@link HttpCall} containing request and error description.
     */
    private static <T> Future<HttpCall<T>> failResponse(Throwable exception, HttpRequest<T> httpRequest) {
        logger.warn("Error occurred while sending HTTP request to a bidder url: {0}", exception, httpRequest.getUri());
        final BidderError.Type errorType =
                exception instanceof TimeoutException || exception instanceof ConnectTimeoutException
                        ? BidderError.Type.timeout
                        : BidderError.Type.generic;

        return Future.succeededFuture(
                HttpCall.failure(httpRequest, BidderError.create(exception.getMessage(), errorType)));
    }

    /**
     * Produces {@link Future} with {@link HttpCall} containing request, response and possible error description
     * (if status code indicates an error).
     */
    private static <T> Future<HttpCall<T>> processResponse(HttpClientResponse response, HttpRequest<T> httpRequest) {
        final int statusCode = response.getStatusCode();
        return Future.succeededFuture(HttpCall.success(httpRequest,
                HttpResponse.of(statusCode, response.getHeaders(), response.getBody()), errorOrNull(statusCode)));
    }

    /**
     * Returns {@link BidderError} if HTTP status code is not successful, or null otherwise.
     */
    private static BidderError errorOrNull(int statusCode) {
        if (statusCode != HttpResponseStatus.OK.code() && statusCode != HttpResponseStatus.NO_CONTENT.code()) {
            return BidderError.create(String.format(
                    "Unexpected status code: %d. Run with request.test = 1 for more info", statusCode),
                    statusCode == HttpResponseStatus.BAD_REQUEST.code()
                            ? BidderError.Type.bad_input
                            : BidderError.Type.bad_server_response);
        }
        return null;
    }

    private <T> Void processHttpCall(Bidder<T> bidder,
                                     BidRequest bidRequest,
                                     ResultBuilder<T> seatBidBuilder,
                                     HttpCall<T> httpCall) {

        seatBidBuilder.addHttpCall(httpCall, makeBids(bidder, httpCall, bidRequest));
        return null;
    }

    private static <T> Result<List<BidderBid>> makeBids(Bidder<T> bidder, HttpCall<T> httpCall, BidRequest bidRequest) {
        if (httpCall.getError() != null || !isOkOrNoContent(httpCall)) {
            return null;
        }
        return bidder.makeBids(toHttpCallWithSafeResponseBody(httpCall), bidRequest);
    }

    /**
     * Returns true if response HTTP status code is equal to 200 or 204, otherwise false.
     */
    private static boolean isOkOrNoContent(HttpCall httpCall) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        return statusCode == HttpResponseStatus.OK.code() || statusCode == HttpResponseStatus.NO_CONTENT.code();
    }

    /**
     * Replaces body of {@link HttpResponse} with empty JSON object if response HTTP status code is equal to 204.
     * <p>
     * Note: this will safe making bids by bidders from JSON parsing error.
     */
    private static <T> HttpCall<T> toHttpCallWithSafeResponseBody(HttpCall<T> httpCall) {
        final HttpResponse response = httpCall.getResponse();
        final int statusCode = response.getStatusCode();

        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            final HttpResponse updatedHttpResponse = HttpResponse.of(statusCode, response.getHeaders(), "{}");
            return HttpCall.success(httpCall.getRequest(), updatedHttpResponse, null);
        }
        return httpCall;
    }

    private static class ResultBuilder<T> {

        final List<HttpRequest<T>> httpRequests;
        final List<BidderError> previousErrors;
        final BidderRequestCompletionTracker completionTracker;

        final Map<HttpRequest<T>, HttpCall<T>> httpCallsRecorded = new HashMap<>();
        final List<BidderBid> bidsRecorded = new ArrayList<>();
        final List<BidderError> errorsRecorded = new ArrayList<>();

        ResultBuilder(List<HttpRequest<T>> httpRequests,
                      List<BidderError> previousErrors,
                      BidderRequestCompletionTracker completionTracker) {
            this.httpRequests = httpRequests;
            this.previousErrors = previousErrors;
            this.completionTracker = completionTracker;
        }

        void addHttpCall(HttpCall<T> httpCall, Result<List<BidderBid>> bidsResult) {
            httpCallsRecorded.put(httpCall.getRequest(), httpCall);

            final List<BidderBid> bids = bidsResult != null ? bidsResult.getValue() : null;
            if (bids != null) {
                bidsRecorded.addAll(bids);
                completionTracker.processBids(bids);
            }

            final List<BidderError> bidderErrors = bidsResult != null ? bidsResult.getErrors() : null;
            if (bidderErrors != null) {
                errorsRecorded.addAll(bidderErrors);
            }
        }

        BidderSeatBid toBidderSeatBid(boolean debugEnabled) {
            final List<HttpCall<T>> httpCalls = new ArrayList<>(httpCallsRecorded.values());
            httpRequests.stream()
                    .filter(httpRequest -> !httpCallsRecorded.containsKey(httpRequest))
                    .map(httpRequest -> HttpCall.success(httpRequest, null, null))
                    .forEach(httpCalls::add);

            // Capture debugging info from the requests
            final List<ExtHttpCall> extHttpCalls = debugEnabled
                    ? httpCalls.stream().map(ResultBuilder::toExt).collect(Collectors.toList())
                    : Collections.emptyList();

            final List<BidderError> errors = errors(previousErrors, httpCalls, errorsRecorded);

            return BidderSeatBid.of(bidsRecorded, extHttpCalls, errors);
        }

        /**
         * Constructs {@link ExtHttpCall} filled with HTTP call information.
         */
        private static <T> ExtHttpCall toExt(HttpCall<T> httpCall) {
            final HttpRequest<T> request = httpCall.getRequest();
            final ExtHttpCall.ExtHttpCallBuilder builder = ExtHttpCall.builder()
                    .uri(request.getUri())
                    .requestbody(request.getBody());

            final HttpResponse response = httpCall.getResponse();
            if (response != null) {
                builder.responsebody(response.getBody());
                builder.status(response.getStatusCode());
            }

            return builder.build();
        }

        /**
         * Assembles all errors for {@link BidderSeatBid} into the list of {@link BidderError}s.
         */
        private static <R> List<BidderError> errors(List<BidderError> requestErrors, List<HttpCall<R>> calls,
                                                    List<BidderError> responseErrors) {

            final List<BidderError> bidderErrors = new ArrayList<>(requestErrors);
            bidderErrors.addAll(
                    Stream.concat(
                            responseErrors.stream(),
                            calls.stream().map(HttpCall::getError).filter(Objects::nonNull))
                            .collect(Collectors.toList()));
            return bidderErrors;
        }
    }

    private static BidderRequestCompletionTrackerFactory completionTrackerFactoryOrFallback(
            BidderRequestCompletionTrackerFactory completionTrackerFactory) {

        return completionTrackerFactory != null
                ? completionTrackerFactory
                : bidRequest -> new NoOpCompletionTracker();
    }

    private static class NoOpCompletionTracker implements BidderRequestCompletionTracker {

        @Override
        public Future<Void> future() {
            return Future.failedFuture("No-op");
        }

        @Override
        public void processBids(List<BidderBid> bids) {

        }
    }
}
