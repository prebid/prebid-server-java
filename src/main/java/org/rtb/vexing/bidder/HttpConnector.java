package org.rtb.vexing.bidder;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.bidder.model.BidderBid;
import org.rtb.vexing.bidder.model.BidderSeatBid;
import org.rtb.vexing.bidder.model.HttpCall;
import org.rtb.vexing.bidder.model.HttpRequest;
import org.rtb.vexing.bidder.model.HttpResponse;
import org.rtb.vexing.bidder.model.Result;
import org.rtb.vexing.model.openrtb.ext.response.ExtHttpCall;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implements HTTP communication functionality common for {@link Bidder}'s.
 * <p>
 * This class exists to help segregate core auction logic and minimize code duplication across the {@link Bidder}
 * implementations.
 * <p>
 * Any logic which can be done within a single Seat goes inside this class.
 * Any logic which requires responses from all Seats goes inside the {@link org.rtb.vexing.auction.ExchangeService}.
 */
public class HttpConnector {

    private static final Logger logger = LoggerFactory.getLogger(HttpConnector.class);

    private final HttpClient httpClient;

    public HttpConnector(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    /**
     * Executes given request to a given bidder.
     */
    public Future<BidderSeatBid> requestBids(Bidder bidder, BidRequest bidRequest) {
        final Result<List<HttpRequest>> httpRequests = bidder.makeHttpRequests(bidRequest);

        final Future<BidderSeatBid> result = Future.future();
        CompositeFuture.join(httpRequests.value.stream()
                .map(httpRequest -> doRequest(httpRequest, bidRequest))
                .collect(Collectors.toList()))
                .setHandler(httpRequestsResult ->
                        result.complete(toBidderSeatBid(bidder, bidRequest, httpRequests.errors,
                                httpRequestsResult.result().list())));

        return result;
    }

    /**
     * Makes an HTTP request and returns {@link Future} that will be eventually completed with success or error result.
     */
    private Future<HttpCall> doRequest(HttpRequest httpRequest, BidRequest bidRequest) {
        final Future<HttpCall> result = Future.future();

        final HttpClientRequest httpClientRequest =
                httpClient.requestAbs(httpRequest.method, httpRequest.uri,
                        response -> handleResponse(response, result, httpRequest))
                        .exceptionHandler(exception -> handleException(exception, result, httpRequest));
        httpClientRequest.headers().addAll(httpRequest.headers);
        if (bidRequest.getTmax() != null && bidRequest.getTmax() > 0) {
            httpClientRequest.setTimeout(bidRequest.getTmax());
        }
        httpClientRequest.end(httpRequest.body);

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
    private void handleException(Throwable exception, Future<HttpCall> result, HttpRequest httpRequest) {
        logger.warn("Error occurred while sending HTTP request to a bidder", exception);
        result.complete(HttpCall.partial(httpRequest, exception.getMessage()));
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
    private BidderSeatBid toBidderSeatBid(Bidder bidder, BidRequest bidRequest, List<String> previousErrors,
                                          List<HttpCall> calls) {
        // If this is a test bid, capture debugging info from the requests
        final List<ExtHttpCall> httpCalls = bidRequest.getTest() == 1
                ? calls.stream().map(HttpConnector::toExt).collect(Collectors.toList())
                : Collections.emptyList();

        final List<Result<List<BidderBid>>> createdBids = calls.stream()
                .filter(httpCall -> StringUtils.isBlank(httpCall.error))
                .map(bidder::makeBids)
                .collect(Collectors.toList());

        final List<BidderBid> bids = createdBids.stream()
                .flatMap(bid -> bid.value.stream())
                .collect(Collectors.toList());

        final List<String> errors = new ArrayList<>(previousErrors);
        errors.addAll(Stream.concat(
                calls.stream().map(httpCall -> httpCall.error).filter(StringUtils::isNotBlank),
                createdBids.stream().flatMap(bidResult -> bidResult.errors.stream()))
                .collect(Collectors.toList()));

        // TODO: by now ext is not filled (same behavior is observed in open-source version), either fill it or
        // delete from seat
        return BidderSeatBid.of(bids, null, httpCalls, errors);
    }

    /**
     * Constructs {@link ExtHttpCall} filled with HTTP call information.
     */
    private static ExtHttpCall toExt(HttpCall httpCall) {
        final ExtHttpCall.ExtHttpCallBuilder builder = ExtHttpCall.builder()
                .uri(httpCall.request.uri)
                .requestbody(httpCall.request.body);
        if (httpCall.response != null) {
            builder.responsebody(httpCall.response.body);
            builder.status(httpCall.response.statusCode);
        }

        return builder.build();
    }
}
