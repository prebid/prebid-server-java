package org.prebid.adapter;

import com.iab.openrtb.request.BidRequest;
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
import lombok.AllArgsConstructor;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.adapter.model.ExchangeCall;
import org.prebid.adapter.model.HttpRequest;
import org.prebid.exception.PreBidException;
import org.prebid.execution.GlobalTimeout;
import org.prebid.model.AdUnitBid;
import org.prebid.model.Bidder;
import org.prebid.model.BidderResult;
import org.prebid.model.MediaType;
import org.prebid.model.PreBidRequestContext;
import org.prebid.model.response.Bid;
import org.prebid.model.response.BidderDebug;
import org.prebid.model.response.BidderStatus;

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
public class HttpConnector {

    private static final Logger logger = LoggerFactory.getLogger(HttpConnector.class);

    private static final Clock CLOCK = Clock.systemDefaultZone();

    private final HttpClient httpClient;

    public HttpConnector(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient);
    }

    public Future<BidderResult> call(Adapter adapter, Bidder bidder, PreBidRequestContext preBidRequestContext) {
        Objects.requireNonNull(adapter);
        Objects.requireNonNull(bidder);
        Objects.requireNonNull(preBidRequestContext);

        final long bidderStarted = CLOCK.millis();

        final List<HttpRequest> httpRequests;
        try {
            httpRequests = adapter.makeHttpRequests(bidder, preBidRequestContext);
        } catch (PreBidException e) {
            return Future.succeededFuture(errorBidderResult(e, bidderStarted));
        }

        return CompositeFuture.join(httpRequests.stream()
                .map(httpRequest -> doRequest(httpRequest, preBidRequestContext.getTimeout()))
                .collect(Collectors.toList()))
                .map(compositeFuture -> toBidderResult(adapter, bidder, preBidRequestContext, bidderStarted,
                        compositeFuture.list()));
    }

    /**
     * Makes an HTTP request and returns {@link Future} that will be eventually completed with success or error result.
     */
    private Future<ExchangeCall> doRequest(HttpRequest httpRequest, GlobalTimeout timeout) {
        final BidRequest bidRequest = httpRequest.getBidRequest();
        final String uri = httpRequest.getUri();

        final String body = Json.encode(bidRequest);
        final BidderDebug.BidderDebugBuilder bidderDebugBuilder = beginBidderDebug(uri, body);
        final Future<ExchangeCall> future = Future.future();

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            handleException(new TimeoutException(), bidderDebugBuilder, future);
            return future;
        }

        final HttpClientRequest httpClientRequest = httpClient.postAbs(uri,
                response -> handleResponse(bidderDebugBuilder, response, future, bidRequest))
                .exceptionHandler(exception -> handleException(exception, bidderDebugBuilder, future))
                .setTimeout(remainingTimeout);

        final MultiMap headers = httpRequest.getHeaders();
        if (headers != null && !headers.isEmpty()) {
            httpClientRequest.headers().addAll(headers);
        }

        httpClientRequest.end(body);

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

    private static void handleResponse(BidderDebug.BidderDebugBuilder bidderDebugBuilder,
                                       HttpClientResponse response, Future<ExchangeCall> future,
                                       BidRequest bidRequest) {
        response
                .bodyHandler(buffer -> future.complete(
                        toExchangeCall(bidRequest, response.statusCode(), buffer.toString(), bidderDebugBuilder)))
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
    private static ExchangeCall toExchangeCall(BidRequest bidRequest, int statusCode, String body,
                                               BidderDebug.BidderDebugBuilder bidderDebugBuilder) {
        final BidderDebug bidderDebug = completeBidderDebug(bidderDebugBuilder, statusCode, body);

        if (statusCode == 204) {
            return ExchangeCall.empty(bidderDebug);
        }

        if (statusCode != 200) {
            logger.warn("Bid response code is {0}, body: {1}", statusCode, body);
            return ExchangeCall.error(bidderDebug, String.format("HTTP status %d; body: %s", statusCode, body));
        }

        final BidResponse bidResponse;
        try {
            bidResponse = Json.decodeValue(body, BidResponse.class);
        } catch (DecodeException e) {
            logger.warn("Error occurred while parsing bid response: {0}", e, body);
            return ExchangeCall.error(bidderDebug, e.getMessage());
        }

        return ExchangeCall.success(bidRequest, bidResponse, bidderDebug);
    }

    /**
     * Transforms {@link ExchangeCall} into single {@link BidderResult} filled with debug information,
     * list of {@link Bid}, {@link BidderStatus}, etc.
     */
    private static BidderResult toBidderResult(Adapter adapter, Bidder bidder,
                                               PreBidRequestContext preBidRequestContext,
                                               long bidderStarted, List<ExchangeCall> exchangeCalls) {
        final Integer responseTime = responseTime(bidderStarted);

        final BidderStatus.BidderStatusBuilder bidderStatusBuilder = BidderStatus.builder()
                .bidder(bidder.getBidderCode())
                .responseTimeMs(responseTime);

        if (preBidRequestContext.isDebug()) {
            bidderStatusBuilder
                    .debug(exchangeCalls.stream().map(ExchangeCall::getBidderDebug).collect(Collectors.toList()));
        }

        if (preBidRequestContext.getPreBidRequest().getApp() == null
                && preBidRequestContext.getUidsCookie().uidFrom(adapter.cookieFamily()) == null) {
            bidderStatusBuilder
                    .noCookie(true)
                    .usersync(adapter.usersyncInfo());
        }

        final List<BidsWithError> bidsWithErrors = exchangeCalls.stream()
                .map(exchangeCall -> bidsWithError(adapter, bidder, exchangeCall, responseTime))
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
            bidsToReturn = dropBidsWithNotValidSize(bids, bidder.getAdUnitBids());
            bidderStatusBuilder.numBids(bidsToReturn.size());
        }

        return BidderResult.of(bidderStatusBuilder.build(), bidsToReturn, timedOut);
    }

    private static int responseTime(long bidderStarted) {
        return Math.toIntExact(CLOCK.millis() - bidderStarted);
    }

    /**
     * Transforms {@link ExchangeCall} into {@link BidsWithError} object with list of bids or error.
     */
    private static BidsWithError bidsWithError(Adapter adapter, Bidder bidder, ExchangeCall exchangeCall,
                                               Integer responseTime) {
        final String error = exchangeCall.getError();
        if (StringUtils.isNotBlank(error)) {
            return BidsWithError.of(Collections.emptyList(), error, exchangeCall.isTimedOut());
        }
        try {
            final List<Bid> bids = adapter.extractBids(bidder, exchangeCall).stream()
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
     * Removes from Bidder result bids with zero width or height if it is not possible to find these values
     * in correspond AdUnitBid
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

    private static BidderResult errorBidderResult(Exception exception, long bidderStarted) {
        logger.warn("Error occurred while constructing bid requests", exception);
        return BidderResult.of(BidderStatus.builder()
                .error(exception.getMessage())
                .responseTimeMs(responseTime(bidderStarted))
                .build(), Collections.emptyList(), false);
    }

    @AllArgsConstructor(staticName = "of")
    @Value
    private static final class BidsWithError {

        List<Bid> bids;

        String error;

        boolean timedOut;
    }
}
