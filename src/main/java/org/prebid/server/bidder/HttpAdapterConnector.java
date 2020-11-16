package org.prebid.server.bidder;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.response.BidResponse;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
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
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.privacy.PrivacyExtractor;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.request.PreBidRequest;
import org.prebid.server.proto.response.Bid;
import org.prebid.server.proto.response.BidderDebug;
import org.prebid.server.proto.response.BidderStatus;
import org.prebid.server.proto.response.MediaType;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

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
    private final PrivacyExtractor privacyExtractor;
    private final Clock clock;
    private final JacksonMapper mapper;

    public HttpAdapterConnector(HttpClient httpClient,
                                PrivacyExtractor privacyExtractor,
                                Clock clock,
                                JacksonMapper mapper) {

        this.httpClient = Objects.requireNonNull(httpClient);
        this.privacyExtractor = Objects.requireNonNull(privacyExtractor);
        this.clock = Objects.requireNonNull(clock);
        this.mapper = Objects.requireNonNull(mapper);
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
    private <T, R> Future<ExchangeCall<T, R>> doRequest(AdapterHttpRequest<T> httpRequest, Timeout timeout,
                                                        TypeReference<R> typeReference) {
        final String uri = httpRequest.getUri();
        final T requestBody = httpRequest.getPayload();
        final String body = requestBody != null ? mapper.encode(requestBody) : null;
        final BidderDebug.BidderDebugBuilder bidderDebugBuilder = beginBidderDebug(uri, body);

        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return failResponse(new TimeoutException("Timeout has been exceeded"), bidderDebugBuilder);
        }

        return httpClient.request(httpRequest.getMethod(), uri, httpRequest.getHeaders(), body, remainingTimeout)
                .compose(response -> processResponse(response, typeReference, requestBody, bidderDebugBuilder))
                .recover(exception -> failResponse(exception, bidderDebugBuilder));
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

    /**
     * Handles request (e.g. read timeout) and response (e.g. connection reset) errors producing
     * {@link ExchangeCall} containing {@link BidderDebug} and error description.
     */
    private static <T, R> Future<ExchangeCall<T, R>> failResponse(Throwable exception,
                                                                  BidderDebug.BidderDebugBuilder bidderDebugBuilder) {
        logger.warn("Error occurred while sending bid request to an exchange: {0}", exception.getMessage());
        logger.debug("Error occurred while sending bid request to an exchange", exception);

        final BidderError error = exception instanceof TimeoutException || exception instanceof ConnectTimeoutException
                ? BidderError.timeout("Timed out")
                : BidderError.generic(exception.getMessage());

        return Future.succeededFuture(ExchangeCall.error(bidderDebugBuilder.build(), error));
    }

    /**
     * Handles {@link HttpClientResponse}, analyzes response status
     * and creates {@link Future} with {@link ExchangeCall} from body content.
     */
    private <T, R> Future<ExchangeCall<T, R>> processResponse(
            HttpClientResponse response, TypeReference<R> responseTypeReference, T request,
            BidderDebug.BidderDebugBuilder bidderDebugBuilder) {

        return Future.succeededFuture(toExchangeCall(
                request, response.getStatusCode(), response.getBody(), responseTypeReference, bidderDebugBuilder));
    }

    /**
     * Transforms HTTP call results into {@link ExchangeCall} filled with debug information,
     * {@link BidResponse} and errors happened along the way.
     */
    private <T, R> ExchangeCall<T, R> toExchangeCall(T request, int statusCode, String body,
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
            bidResponse = mapper.decodeValue(body, responseTypeReference);
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
                && preBidRequestContext.getUidsCookie().uidFrom(usersyncer.getCookieFamilyName()) == null) {

            final Privacy privacy = privacyExtractor.validPrivacyFrom(preBidRequest);
            bidderStatusBuilder
                    .noCookie(true)
                    .usersync(UsersyncInfoAssembler.from(usersyncer).withPrivacy(privacy).assemble());
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
            return Result.withError(error);
        }
        try {
            final List<Bid> bids = adapter.extractBids(adapterRequest, exchangeCall).stream()
                    .map(bidBuilder -> bidBuilder.responseTimeMs(responseTime))
                    .map(Bid.BidBuilder::build)
                    .collect(Collectors.toList());
            return Result.withValues(bids);
        } catch (PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
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
