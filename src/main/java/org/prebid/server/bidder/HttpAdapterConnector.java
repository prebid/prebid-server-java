package org.prebid.server.bidder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.response.BidResponse;
import io.netty.channel.ConnectTimeoutException;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.AdapterResponse;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.BidsWithError;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.BidderDebug;
import org.prebid.server.proto.response.BidderStatus;
import org.prebid.server.proto.response.MediaType;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Implements HTTP communication functionality common for {@link Adapter}'s.
 * <p>
 * This class exists to help segregate core auction logic and minimize code duplication across the {@link Adapter}
 * implementations.
 */
public class HttpAdapterConnector {

    private static final Logger logger = LoggerFactory.getLogger(HttpAdapterConnector.class);

    private final HttpClient httpClient;
    private final Clock clock;

    public HttpAdapterConnector(HttpClient httpClient, Clock clock) {
        this.httpClient = Objects.requireNonNull(httpClient);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Executes HTTP requests for particular {@link Adapter} and returns {@link AdapterResponse}
     */
    public <T, R> Future<AdapterResponse> call(Adapter<T, R> adapter, Usersyncer usersyncer,
                                               AdapterRequest adapterRequest,
                                               PreBidRequestContext preBidRequestContext) {
        final long bidderStarted = clock.millis();

        final List<AdapterHttpRequest<T>> httpRequests;
        try {
            httpRequests = adapter.makeHttpRequests(adapterRequest, preBidRequestContext);
        } catch (PreBidException e) {
            return Future.succeededFuture(errorBidderResult(e, bidderStarted, adapterRequest.getBidderCode()));
        }

        return CompositeFuture.join(httpRequests.stream()
                .map(httpRequest -> doRequest(httpRequest, preBidRequestContext.getTimeout(),
                        adapter.responseTypeReference()))
                .collect(Collectors.toList()))
                .map(compositeFuture -> toBidderResult(adapter, usersyncer, adapterRequest, preBidRequestContext,
                        bidderStarted, compositeFuture.list()));
    }

    /**
     * Makes an HTTP request and returns {@link Future} that will be eventually completed with success or error result.
     */
    private <T, R> Future<ExchangeCall> doRequest(AdapterHttpRequest<T> httpRequest, Timeout timeout,
                                                  TypeReference<R> typeReference) {
        final T requestBody = httpRequest.getPayload();
        final String uri = httpRequest.getUri();

        final String body = requestBody != null ? Json.encode(requestBody) : null;
        final BidderDebug.BidderDebugBuilder bidderDebugBuilder = beginBidderDebug(uri, body);
        final Future<ExchangeCall> future = Future.future();

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            handleException(new TimeoutException(), bidderDebugBuilder, future);
            return future;
        }

        final HttpClientRequest httpClientRequest = httpClient.requestAbs(httpRequest.getMethod(), uri,
                response -> handleResponse(bidderDebugBuilder, response, typeReference, future, requestBody))
                .exceptionHandler(exception -> handleException(exception, bidderDebugBuilder, future))
                .setTimeout(remainingTimeout);

        final MultiMap headers = httpRequest.getHeaders();
        if (headers != null && !headers.isEmpty()) {
            httpClientRequest.headers().addAll(headers);
        }

        if (body != null) {
            httpClientRequest.end(body);
        } else {
            httpClientRequest.end();
        }

        return future;
    }

    private static BidderDebug.BidderDebugBuilder beginBidderDebug(String url, String bidRequestBody) {
        return BidderDebug.builder()
                .requestUri(url)
                .requestBody(bidRequestBody);
    }

    private static BidderDebug completeBidderDebug(BidderDebug.BidderDebugBuilder bidderDebugBuilder, int statusCode,
                                                   String body) {
        return bidderDebugBuilder
                .responseBody(body)
                .statusCode(statusCode)
                .build();
    }

    private static <T, R> void handleResponse(BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                              HttpClientResponse response, TypeReference<R> responseTypeReference,
                                              Future<ExchangeCall> future, T request) {
        response
                .bodyHandler(buffer -> future.complete(
                        toExchangeCall(request, response.statusCode(), buffer.toString(), responseTypeReference,
                                bidderDebugBuilder)))
                .exceptionHandler(exception -> handleException(exception, bidderDebugBuilder, future));
    }

    /**
     * Handles request (e.g. read timeout) and response (e.g. connection reset) errors producing
     * {@link ExchangeCall} containing {@link BidderDebug} and error description.
     */
    private static void handleException(Throwable exception, BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                        Future<ExchangeCall> future) {
        logger.warn("Error occurred while sending bid request to an exchange", exception);
        final BidderDebug bidderDebug = bidderDebugBuilder.build();
        future.complete(exception instanceof TimeoutException || exception instanceof ConnectTimeoutException
                ? ExchangeCall.timeout(bidderDebug, "Timed out")
                : ExchangeCall.error(bidderDebug, exception.getMessage()));
    }

    /**
     * Transforms HTTP call results into {@link ExchangeCall} filled with debug information,
     * {@link BidResponse} and errors happened along the way.
     */
    private static <T, R> ExchangeCall toExchangeCall(T request, int statusCode, String body,
                                                      TypeReference<R> responseTypeReference,
                                                      BidderDebug.BidderDebugBuilder bidderDebugBuilder) {
        final BidderDebug bidderDebug = completeBidderDebug(bidderDebugBuilder, statusCode, body);

        if (statusCode == 204) {
            return ExchangeCall.empty(bidderDebug);
        }

        if (statusCode != 200) {
            logger.warn("Bid response code is {0}, body: {1}", statusCode, body);
            return ExchangeCall.error(bidderDebug, String.format("HTTP status %d; body: %s", statusCode, body));
        }

        final R bidResponse;
        try {
            bidResponse = Json.decodeValue(body, responseTypeReference);
        } catch (DecodeException e) {
            logger.warn("Error occurred while parsing bid response: {0}", e, body);
            return ExchangeCall.error(bidderDebug, e.getMessage());
        }

        return ExchangeCall.success(request, bidResponse, bidderDebug);
    }

    /**
     * Transforms {@link ExchangeCall} into single {@link AdapterResponse} filled with debug information,
     * list of {@link Bid}, {@link BidderStatus}, etc.
     */
    private <T, R> AdapterResponse toBidderResult(
            Adapter<T, R> adapter, Usersyncer usersyncer, AdapterRequest adapterRequest,
            PreBidRequestContext preBidRequestContext, long bidderStarted, List<ExchangeCall<T, R>> exchangeCalls) {

        final Integer responseTime = responseTime(bidderStarted);

        final BidderStatus.BidderStatusBuilder bidderStatusBuilder = BidderStatus.builder()
                .bidder(adapterRequest.getBidderCode())
                .responseTimeMs(responseTime);

        if (preBidRequestContext.isDebug()) {
            bidderStatusBuilder
                    .debug(exchangeCalls.stream().map(ExchangeCall::getBidderDebug).collect(Collectors.toList()));
        }

        if (preBidRequestContext.getPreBidRequest().getApp() == null
                && preBidRequestContext.getUidsCookie().uidFrom(usersyncer.cookieFamilyName()) == null) {
            bidderStatusBuilder
                    .noCookie(true)
                    .usersync(usersyncer.usersyncInfo());
        }

        final List<BidsWithError> bidsWithErrors = exchangeCalls.stream()
                .map(exchangeCall -> bidsWithError(adapter, adapterRequest, exchangeCall, responseTime))
                .collect(Collectors.toList());

        final BidsWithError failedBidsWithError = failedBidsWithError(bidsWithErrors);
        final List<Bid> bids = bidsWithErrors.stream()
                .flatMap(bidsWithError -> bidsWithError.getBids().stream())
                .collect(Collectors.toList());

        boolean timedOut = false;
        List<Bid> bidsToReturn = Collections.emptyList();

        if (failedBidsWithError != null
                && (!adapter.tolerateErrors() || (adapter.tolerateErrors() && bids.isEmpty()))) {
            bidderStatusBuilder.error(failedBidsWithError.getError());
            timedOut = failedBidsWithError.isTimedOut();
        } else if (bids.isEmpty()) {
            bidderStatusBuilder.noBid(true);
        } else {
            bidsToReturn = dropBidsWithNotValidSize(bids, adapterRequest.getAdUnitBids());
            bidderStatusBuilder.numBids(bidsToReturn.size());
        }

        return AdapterResponse.of(bidderStatusBuilder.build(), bidsToReturn, timedOut);
    }

    private int responseTime(long bidderStarted) {
        return Math.toIntExact(clock.millis() - bidderStarted);
    }

    /**
     * Transforms {@link ExchangeCall} into {@link BidsWithError} object with list of bids or error.
     */
    private static <T, R> BidsWithError bidsWithError(Adapter<T, R> adapter, AdapterRequest adapterRequest,
                                                      ExchangeCall<T, R> exchangeCall, Integer responseTime) {
        final String error = exchangeCall.getError();
        if (StringUtils.isNotBlank(error)) {
            return BidsWithError.of(Collections.emptyList(), error, exchangeCall.isTimedOut());
        }
        try {
            final List<Bid> bids = adapter.extractBids(adapterRequest, exchangeCall).stream()
                    .map(bidBuilder -> bidBuilder.responseTimeMs(responseTime))
                    .map(Bid.BidBuilder::build)
                    .collect(Collectors.toList());
            return BidsWithError.of(bids, null, false);
        } catch (PreBidException e) {
            return BidsWithError.of(Collections.emptyList(), e.getMessage(), false);
        }
    }

    /**
     * Searches for last error in list of {@link BidsWithError}
     */
    private static BidsWithError failedBidsWithError(List<BidsWithError> bidsWithErrors) {
        final ListIterator<BidsWithError> iterator = bidsWithErrors.listIterator(bidsWithErrors.size());
        while (iterator.hasPrevious()) {
            final BidsWithError current = iterator.previous();
            if (StringUtils.isNotBlank(current.getError())) {
                return current;
            }
        }
        return null;
    }

    /**
     * Removes bids with zero width or height if it is not possible to find these values in correspond AdUnitBid
     */
    private static List<Bid> dropBidsWithNotValidSize(List<Bid> bids, List<AdUnitBid> adUnitBids) {
        final List<Bid> notValidBids = bids.stream()
                .filter(bid -> bid.getMediaType() == MediaType.banner
                        && (bid.getHeight() == null || bid.getHeight() == 0
                        || bid.getWidth() == null || bid.getWidth() == 0))
                .collect(Collectors.toList());

        if (notValidBids.isEmpty()) {
            return bids;
        }

        // bids which are not in invalid list are valid
        final List<Bid> validBids = new ArrayList<>(bids);
        validBids.removeAll(notValidBids);

        for (final Bid bid : notValidBids) {
            final Optional<AdUnitBid> matchingAdUnit = adUnitBids.stream()
                    .filter(adUnitBid -> adUnitBid.getAdUnitCode().equals(bid.getCode())
                            && adUnitBid.getBidId().equals(bid.getBidId()) && adUnitBid.getSizes().size() == 1)
                    .findAny();
            if (matchingAdUnit.isPresent()) {
                final Format format = matchingAdUnit.get().getSizes().get(0);
                // IMPORTANT: see javadoc in Bid class
                bid.setWidth(format.getW()).setHeight(format.getH());
                validBids.add(bid);
            } else {
                logger.warn("Bid was rejected for bidder {0} because no size was defined", bid.getBidder());
            }
        }

        return validBids;
    }

    private AdapterResponse errorBidderResult(Exception exception, long bidderStarted, String bidder) {
        logger.warn("Error occurred while constructing bid requests", exception);
        return AdapterResponse.of(BidderStatus.builder()
                .bidder(bidder)
                .error(exception.getMessage())
                .responseTimeMs(responseTime(bidderStarted))
                .build(), Collections.emptyList(), false);
    }
}
