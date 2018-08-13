package org.prebid.server.bidder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import com.iab.openrtb.response.BidResponse;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.http.HttpResponseStatus;
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
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.model.AdUnitBid;
import org.prebid.server.auction.model.AdapterRequest;
import org.prebid.server.auction.model.AdapterResponse;
import org.prebid.server.auction.model.PreBidRequestContext;
import org.prebid.server.bidder.model.AdapterHttpRequest;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.ExchangeCall;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.request.PreBidRequest;
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
            final String message = e.getMessage();
            return Future.succeededFuture(AdapterResponse.of(BidderStatus.builder()
                    .bidder(adapterRequest.getBidderCode())
                    .error(message)
                    .responseTimeMs(responseTime(bidderStarted))
                    .build(), Collections.emptyList(), BidderError.badInput(message)));
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
        // Exception handler can be called more than one time, so all we can do is just to log the error
        if (future.isComplete()) {
            logger.warn("Exception handler was called after processing has been completed: {0}",
                    exception.getMessage());
            return;
        }

        logger.warn("Error occurred while sending bid request to an exchange", exception);
        final BidderDebug bidderDebug = bidderDebugBuilder.build();

        final BidderError error = exception instanceof TimeoutException || exception instanceof ConnectTimeoutException
                ? BidderError.timeout("Timed out")
                : BidderError.generic(exception.getMessage());

        future.complete(ExchangeCall.error(bidderDebug, error));
    }

    /**
     * Transforms HTTP call results into {@link ExchangeCall} filled with debug information,
     * {@link BidResponse} and errors happened along the way.
     */
    private static <T, R> ExchangeCall toExchangeCall(T request, int statusCode, String body,
                                                      TypeReference<R> responseTypeReference,
                                                      BidderDebug.BidderDebugBuilder bidderDebugBuilder) {
        final BidderDebug bidderDebug = completeBidderDebug(bidderDebugBuilder, statusCode, body);

        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return ExchangeCall.empty(bidderDebug);
        }

        if (statusCode != HttpResponseStatus.OK.code()) {
            return ExchangeCall.error(bidderDebug,
                    BidderError.create(String.format("HTTP status %d; body: %s", statusCode, body),
                            statusCode == HttpResponseStatus.BAD_REQUEST.code()
                                    ? BidderError.Type.bad_input
                                    : BidderError.Type.bad_server_response));
        }

        final R bidResponse;
        try {
            bidResponse = Json.decodeValue(body, responseTypeReference);
        } catch (DecodeException e) {
            return ExchangeCall.error(bidderDebug, BidderError.badServerResponse(e.getMessage()));
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

        final PreBidRequest preBidRequest = preBidRequestContext.getPreBidRequest();
        if (preBidRequest.getApp() == null
                && preBidRequestContext.getUidsCookie().uidFrom(usersyncer.cookieFamilyName()) == null) {

            final String gdpr = gdprFrom(preBidRequest.getRegs());
            final String gdprConsent = gdprConsentFrom(preBidRequest.getUser());

            bidderStatusBuilder
                    .noCookie(true)
                    .usersync(usersyncer.usersyncInfo().withGdpr(gdpr, gdprConsent));
        }

        final List<Result<List<Bid>>> bidsWithErrors = exchangeCalls.stream()
                .map(exchangeCall -> bidsWithError(adapter, adapterRequest, exchangeCall, responseTime))
                .collect(Collectors.toList());

        final Result<List<Bid>> failedBidsWithError = failedBidsWithError(bidsWithErrors);
        final List<Bid> bids = bidsWithErrors.stream()
                .flatMap(bidsWithError -> bidsWithError.getValue().stream())
                .collect(Collectors.toList());

        BidderError errorToReturn = null;
        List<Bid> bidsToReturn = Collections.emptyList();

        if (failedBidsWithError != null
                && (!adapter.tolerateErrors() || (adapter.tolerateErrors() && bids.isEmpty()))) {
            errorToReturn = failedBidsWithError.getErrors().get(0);
            bidderStatusBuilder.error(errorToReturn.getMessage());
        } else if (bids.isEmpty()) {
            bidderStatusBuilder.noBid(true);
        } else {
            bidsToReturn = dropBidsWithNotValidSize(bids, adapterRequest.getAdUnitBids());
            bidderStatusBuilder.numBids(bidsToReturn.size());
        }

        return AdapterResponse.of(bidderStatusBuilder.build(), bidsToReturn, errorToReturn);
    }

    private static String gdprFrom(Regs regs) {
        final ObjectNode extRegsNode = regs != null ? regs.getExt() : null;
        final ExtRegs extRegs;
        try {
            extRegs = extRegsNode != null ? Json.mapper.treeToValue(extRegsNode, ExtRegs.class) : null;
        } catch (JsonProcessingException e) {
            return "";
        }

        final String gdpr = extRegs != null ? Integer.toString(extRegs.getGdpr()) : "";
        return ObjectUtils.notEqual(gdpr, "1") && ObjectUtils.notEqual(gdpr, "0") ? "" : gdpr;
    }

    private static String gdprConsentFrom(User user) {
        final ObjectNode extUserNode = user != null ? user.getExt() : null;
        ExtUser extUser;
        try {
            extUser = extUserNode != null ? Json.mapper.treeToValue(extUserNode, ExtUser.class) : null;
        } catch (JsonProcessingException e) {
            extUser = null;
        }

        final String gdprConsent = extUser != null ? extUser.getConsent() : "";
        return ObjectUtils.firstNonNull(gdprConsent, "");
    }

    private int responseTime(long bidderStarted) {
        return Math.toIntExact(clock.millis() - bidderStarted);
    }

    /**
     * Transforms {@link ExchangeCall} into {@link Result}&lt;{@link List}&lt;{@link Bid}&gt;&gt; object with list of
     * bids and error.
     */
    private static <T, R> Result<List<Bid>> bidsWithError(Adapter<T, R> adapter, AdapterRequest adapterRequest,
                                                          ExchangeCall<T, R> exchangeCall, Integer responseTime) {
        final BidderError error = exchangeCall.getError();
        if (error != null) {
            return Result.emptyWithError(error);
        }
        try {
            final List<Bid> bids = adapter.extractBids(adapterRequest, exchangeCall).stream()
                    .map(bidBuilder -> bidBuilder.responseTimeMs(responseTime))
                    .map(Bid.BidBuilder::build)
                    .collect(Collectors.toList());
            return Result.of(bids, Collections.emptyList());
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    /**
     * Searches for last error in list of {@link Result}&lt;{@link List}&lt;{@link Bid}&gt;&gt;
     */
    private static <U extends Result<List<Bid>>> U failedBidsWithError(List<U> bidsWithErrors) {
        final ListIterator<U> iterator = bidsWithErrors.listIterator(bidsWithErrors.size());
        while (iterator.hasPrevious()) {
            final U current = iterator.previous();
            if (CollectionUtils.isNotEmpty(current.getErrors())) {
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
}
