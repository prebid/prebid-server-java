package org.prebid.server.bidder;

import com.iab.openrtb.request.BidRequest;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClientResponse;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
public class HttpBidderRequester<T> implements BidderRequester {

    private static final Logger logger = LoggerFactory.getLogger(HttpBidderRequester.class);

    private final Bidder<T> bidder;
    private final HttpClient httpClient;

    public HttpBidderRequester(Bidder<T> bidder, HttpClient httpClient) {
        this.bidder = Objects.requireNonNull(bidder);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    /**
     * Executes given request to a given bidder.
     */
    public Future<BidderSeatBid> requestBids(BidRequest bidRequest, Timeout timeout) {
        final Result<List<HttpRequest<T>>> httpRequestsWithErrors = bidder.makeHttpRequests(bidRequest);

        final List<BidderError> bidderErrors = httpRequestsWithErrors.getErrors();
        final List<HttpRequest<T>> httpRequests = httpRequestsWithErrors.getValue();

        return CollectionUtils.isEmpty(httpRequests)
                ? emptyBidderSeatBidWithErrors(bidderErrors)
                : CompositeFuture.join(httpRequests.stream()
                .map(httpRequest -> doRequest(httpRequest, timeout))
                .collect(Collectors.toList()))
                .map(httpRequestsResult -> toBidderSeatBid(bidRequest, bidderErrors, httpRequestsResult.list()));
    }

    /**
     * Creates {@link Future<BidderSeatBid>} with empty {@link List<BidderBid>} and {@link List<ExtHttpCall>} with
     * {@link List<BidderError>}. If errors list is empty, creates error which indicates of bidder unexpected behaviour.
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
    private Future<HttpCall<T>> doRequest(HttpRequest<T> httpRequest, Timeout timeout) {
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
     * Handles request (e.g. read timeout) and response (e.g. connection reset) errors producing partial
     * {@link HttpCall} containing request and error description.
     */
    private static <T> Future<HttpCall<T>> failResponse(Throwable exception, HttpRequest<T> httpRequest) {
        logger.warn("Error occurred while sending HTTP request to a bidder", exception);
        final BidderError.Type errorType =
                exception instanceof TimeoutException || exception instanceof ConnectTimeoutException
                        ? BidderError.Type.timeout
                        : BidderError.Type.generic;

        return Future.succeededFuture(
                HttpCall.failure(httpRequest, BidderError.create(exception.getMessage(), errorType)));
    }

    private static <T> Future<HttpCall<T>> processResponse(HttpClientResponse response, HttpRequest<T> httpRequest) {
        final Future<HttpCall<T>> future = Future.future();
        response
                .bodyHandler(buffer -> future.complete(
                        toHttpCall(response.statusCode(), buffer.toString(), response.headers(), httpRequest)))
                .exceptionHandler(future::fail);
        return future;
    }

    /**
     * Produces full {@link HttpCall} containing request, response and possible error description (if status code
     * indicates an error).
     */
    private static <T> HttpCall<T> toHttpCall(int statusCode, String body, MultiMap headers,
                                              HttpRequest<T> httpRequest) {
        return HttpCall.success(httpRequest, HttpResponse.of(statusCode, headers, body), errorOrNull(statusCode));
    }

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

    /**
     * Transforms HTTP call results into single {@link BidderSeatBid} filled with debug information, bids and errors
     * happened along the way.
     */
    private BidderSeatBid toBidderSeatBid(BidRequest bidRequest, List<BidderError> previousErrors,
                                          List<HttpCall<T>> calls) {
        // If this is a test bid, capture debugging info from the requests
        final List<ExtHttpCall> httpCalls = Objects.equals(bidRequest.getTest(), 1)
                ? calls.stream().map(HttpBidderRequester::toExt).collect(Collectors.toList())
                : Collections.emptyList();

        final List<Result<List<BidderBid>>> createdBids = calls.stream()
                .filter(httpCall -> httpCall.getError() == null)
                .filter(httpCall -> httpCall.getResponse().getStatusCode() == HttpResponseStatus.OK.code())
                .map(httpCall -> bidder.makeBids(httpCall, bidRequest))
                .collect(Collectors.toList());

        final List<BidderBid> bids = createdBids.stream()
                .flatMap(bidderBid -> bidderBid.getValue().stream())
                .collect(Collectors.toList());

        final List<BidderError> bidderErrors = errors(previousErrors, calls, createdBids);

        return BidderSeatBid.of(bids, httpCalls, bidderErrors);
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
     * Assembles all errors for {@link BidderSeatBid} into the list of {@link List}&lt;{@link BidderError}&gt;
     */
    private static <R> List<BidderError> errors(List<BidderError> previousErrors, List<HttpCall<R>> calls,
                                                List<Result<List<BidderBid>>> createdBids) {
        final List<BidderError> bidderErrors = new ArrayList<>(previousErrors);
        bidderErrors.addAll(
                Stream.concat(
                        createdBids.stream().flatMap(bidResult -> bidResult.getErrors().stream()),
                        calls.stream().map(HttpCall::getError).filter(Objects::nonNull))
                        .collect(Collectors.toList()));
        return bidderErrors;
    }
}
