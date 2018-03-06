package org.prebid.server.bidder;

import com.iab.openrtb.request.BidRequest;
import io.netty.channel.ConnectTimeoutException;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.execution.GlobalTimeout;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;

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
public class HttpBidderRequester implements BidderRequester {

    private static final Logger logger = LoggerFactory.getLogger(HttpBidderRequester.class);

    private final Bidder bidder;
    private final HttpClient httpClient;

    public HttpBidderRequester(Bidder bidder, HttpClient httpClient) {
        this.bidder = Objects.requireNonNull(bidder);
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    /**
     * Executes given request to a given bidder.
     */
    public Future<BidderSeatBid> requestBids(BidRequest bidRequest, GlobalTimeout timeout) {
        final Result<List<HttpRequest>> httpRequests = bidder.makeHttpRequests(bidRequest);

        return CompositeFuture.join(httpRequests.getValue().stream()
                .map(httpRequest -> doRequest(httpRequest, timeout))
                .collect(Collectors.toList()))
                .map(httpRequestsResult -> toBidderSeatBid(bidder, bidRequest, httpRequests.getErrors(),
                        httpRequestsResult.list()));
    }

    /**
     * Makes an HTTP request and returns {@link Future} that will be eventually completed with success or error result.
     */
    private Future<HttpCall> doRequest(HttpRequest httpRequest, GlobalTimeout timeout) {
        final Future<HttpCall> result = Future.future();

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            handleException(new TimeoutException("Timeout has been exceeded"), result, httpRequest);
            return result;
        }

        final HttpClientRequest httpClientRequest =
                httpClient.requestAbs(httpRequest.getMethod(), httpRequest.getUri(),
                        response -> handleResponse(response, result, httpRequest))
                        .exceptionHandler(exception -> handleException(exception, result, httpRequest));
        httpClientRequest.headers().addAll(httpRequest.getHeaders());
        httpClientRequest.setTimeout(remainingTimeout);
        httpClientRequest.end(httpRequest.getBody());

        return result;
    }

    private void handleResponse(HttpClientResponse response, Future<HttpCall> result, HttpRequest httpRequest) {
        response
                .bodyHandler(buffer -> result.complete(
                        toCall(response.statusCode(), buffer.toString(), response.headers(), httpRequest)))
                .exceptionHandler(exception -> handleException(exception, result, httpRequest));
    }

    /**
     * Handles request (e.g. read timeout) and response (e.g. connection reset) errors producing partial
     * {@link HttpCall} containing request and error description.
     */
    private static void handleException(Throwable exception, Future<HttpCall> result, HttpRequest httpRequest) {
        logger.warn("Error occurred while sending HTTP request to a bidder", exception);
        final boolean isTimedOut = exception instanceof TimeoutException
                || exception instanceof ConnectTimeoutException;
        result.complete(HttpCall.partial(httpRequest, exception.getMessage(), isTimedOut));
    }

    /**
     * Produces full {@link HttpCall} containing request, response and possible error description (if status code
     * indicates an error).
     */
    private HttpCall toCall(int statusCode, String body, MultiMap headers, HttpRequest httpRequest) {
        return HttpCall.full(httpRequest, HttpResponse.of(statusCode, headers, body), errorOrNull(statusCode));
    }

    private static String errorOrNull(int statusCode) {
        return statusCode < 200 || statusCode >= 400
                ? String.format("Server responded with failure status: %d. Set request.test = 1 for debugging info.",
                statusCode)
                : null;
    }

    /**
     * Transforms HTTP call results into single {@link BidderSeatBid} filled with debug information, bids and errors
     * happened along the way.
     */
    private BidderSeatBid toBidderSeatBid(Bidder bidder, BidRequest bidRequest, List<BidderError> previousErrors,
                                          List<HttpCall> calls) {
        // If this is a test bid, capture debugging info from the requests
        final List<ExtHttpCall> httpCalls = bidRequest.getTest() == 1
                ? calls.stream().map(HttpBidderRequester::toExt).collect(Collectors.toList())
                : Collections.emptyList();

        final List<Result<List<BidderBid>>> createdBids = calls.stream()
                .filter(httpCall -> StringUtils.isBlank(httpCall.getError()))
                .map(httpCall -> bidder.makeBids(httpCall, bidRequest))
                .collect(Collectors.toList());

        final List<BidderBid> bids = createdBids.stream()
                .flatMap(bid -> bid.getValue().stream())
                .collect(Collectors.toList());

        final List<BidderError> bidderErrors = errors(previousErrors, calls, createdBids);

        return BidderSeatBid.of(bids, httpCalls, bidderErrors);
    }

    /**
     * Assembles all errors for {@link BidderSeatBid} into the list of {@link List}&lt;{@link BidderError}&gt;
     */
    private List<BidderError> errors(List<BidderError> previousErrors, List<HttpCall> calls,
                                     List<Result<List<BidderBid>>> createdBids) {

        final List<BidderError> bidderErrors = new ArrayList<>(previousErrors);
        bidderErrors.addAll(
                Stream.concat(
                        createdBids.stream().flatMap(bidResult -> bidResult.getErrors().stream()),
                        calls.stream().filter(call -> StringUtils.isNotBlank(call.getError()))
                                .map(call -> BidderError.of(call.getError(), call.isTimedOut())))
                        .collect(Collectors.toList()));

        return bidderErrors;
    }

    /**
     * Constructs {@link ExtHttpCall} filled with HTTP call information.
     */
    private static ExtHttpCall toExt(HttpCall httpCall) {
        final HttpRequest request = httpCall.getRequest();
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
}
