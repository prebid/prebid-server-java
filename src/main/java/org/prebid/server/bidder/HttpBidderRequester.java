package org.prebid.server.bidder;

import com.iab.openrtb.request.BidRequest;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.model.BidRejectionReason;
import org.prebid.server.auction.model.BidRejectionTracker;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderCallType;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.CompositeBidderResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.proto.openrtb.ext.response.FledgeAuctionConfig;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

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
    private final BidderErrorNotifier bidderErrorNotifier;
    private final HttpBidderRequestEnricher requestEnricher;
    private final JacksonMapper mapper;

    public HttpBidderRequester(HttpClient httpClient,
                               BidderRequestCompletionTrackerFactory completionTrackerFactory,
                               BidderErrorNotifier bidderErrorNotifier,
                               HttpBidderRequestEnricher requestEnricher,
                               JacksonMapper mapper) {

        this.httpClient = Objects.requireNonNull(httpClient);
        this.completionTrackerFactory = completionTrackerFactoryOrFallback(completionTrackerFactory);
        this.bidderErrorNotifier = Objects.requireNonNull(bidderErrorNotifier);
        this.requestEnricher = Objects.requireNonNull(requestEnricher);
        this.mapper = Objects.requireNonNull(mapper);
    }

    /**
     * Executes given request to a given bidder.
     */
    public <T> Future<BidderSeatBid> requestBids(Bidder<T> bidder,
                                                 BidderRequest bidderRequest,
                                                 BidRejectionTracker bidRejectionTracker,
                                                 Timeout timeout,
                                                 CaseInsensitiveMultiMap requestHeaders,
                                                 BidderAliases aliases,
                                                 boolean debugEnabled) {

        final String bidderName = bidderRequest.getBidder();
        final BidRequest bidRequest = bidderRequest.getBidRequest();

        final Result<List<HttpRequest<T>>> httpRequestsWithErrors = bidder.makeHttpRequests(bidRequest);
        final List<BidderError> errors = httpRequestsWithErrors.getErrors();
        final List<HttpRequest<T>> httpRequests = enrichRequests(
                bidderName, httpRequestsWithErrors.getValue(), requestHeaders, aliases, bidRequest);
        recordBidderProvidedErrors(bidRejectionTracker, errors);

        if (CollectionUtils.isEmpty(httpRequests)) {
            return emptyBidderSeatBidWithErrors(errors);
        }

        final String storedResponse = bidderRequest.getStoredResponse();

        // stored response available only for single request interaction for the moment.
        final Stream<Future<BidderCall<T>>> httpCalls = isStoredResponse(httpRequests, storedResponse, bidderName)
                ? Stream.of(makeStoredHttpCall(httpRequests.get(0), storedResponse))
                : httpRequests.stream().map(httpRequest -> doRequest(httpRequest, timeout));

        // httpCalls contains recovered and mapped to succeeded Future<BidderHttpCall> with error inside
        final BidderRequestCompletionTracker completionTracker = completionTrackerFactory.create(bidRequest);
        final ResultBuilder<T> resultBuilder = new ResultBuilder<>(
                httpRequests, errors, completionTracker, bidRejectionTracker, mapper);

        final List<Future<Void>> httpRequestFutures = httpCalls
                .map(httpCallFuture -> httpCallFuture
                        .map(httpCall -> bidderErrorNotifier.processTimeout(httpCall, bidder))
                        .map(httpCall -> processHttpCall(bidder, bidRequest, resultBuilder, httpCall)))
                .toList();

        return CompositeFuture.any(
                        CompositeFuture.join(new ArrayList<>(httpRequestFutures)),
                        completionTracker.future())
                .map(ignored -> resultBuilder.toBidderSeatBid(debugEnabled))
                .onSuccess(seatBid -> bidRejectionTracker.restoreFromRejection(seatBid.getBids()));
    }

    private <T> List<HttpRequest<T>> enrichRequests(String bidderName,
                                                    List<HttpRequest<T>> httpRequests,
                                                    CaseInsensitiveMultiMap requestHeaders,
                                                    BidderAliases aliases,
                                                    BidRequest bidRequest) {

        return httpRequests.stream().map(httpRequest -> httpRequest.toBuilder()
                        .headers(requestEnricher.enrichHeaders(
                                bidderName, httpRequest.getHeaders(), requestHeaders, aliases, bidRequest))
                        .build())
                .toList();
    }

    private static void recordBidderProvidedErrors(BidRejectionTracker rejectionTracker, List<BidderError> errors) {
        errors.stream()
                .filter(error -> CollectionUtils.isNotEmpty(error.getImpIds()))
                .forEach(error -> rejectionTracker.reject(
                        error.getImpIds(), BidRejectionReason.fromBidderError(error)));
    }

    private <T> boolean isStoredResponse(List<HttpRequest<T>> httpRequests, String storedResponse, String bidder) {
        if (StringUtils.isBlank(storedResponse)) {
            return false;
        }

        if (httpRequests.size() > 1) {
            logger.warn("""
                            More than one request was created for stored response, when only single stored response \
                            per bidder is supported for the moment. Request to real {} bidder will be performed.""",
                    bidder);
            return false;
        }

        return true;
    }

    private <T> Future<BidderCall<T>> makeStoredHttpCall(HttpRequest<T> httpRequest, String storedResponse) {
        final HttpResponse httpResponse = HttpResponse.of(HttpResponseStatus.OK.code(), null, storedResponse);
        return Future.succeededFuture(BidderCall.storedHttp(httpRequest, httpResponse));
    }

    /**
     * Creates {@link Future<BidderSeatBid>} with empty list of {@link BidderBid}s
     * and list of {@link ExtHttpCall}s with list of {@link BidderError}s.
     * If errors list is empty, creates error which indicates of bidder unexpected behaviour.
     */
    private Future<BidderSeatBid> emptyBidderSeatBidWithErrors(List<BidderError> bidderErrors) {
        final List<BidderError> errors = bidderErrors.isEmpty()
                ? Collections.singletonList(BidderError.failedToRequestBids(
                "The bidder failed to generate any bid requests, but also failed to generate an error"))
                : bidderErrors;

        return Future.succeededFuture(BidderSeatBid.builder()
                .errors(errors)
                .build());
    }

    /**
     * Makes an HTTP request and returns {@link Future} that will be eventually completed with success or error result.
     */
    private <T> Future<BidderCall<T>> doRequest(HttpRequest<T> httpRequest, Timeout timeout) {
        final long remainingTimeout = timeout.remaining();
        if (remainingTimeout <= 0) {
            return failResponse(new TimeoutException("Timeout has been exceeded"), httpRequest);
        }

        return createRequest(httpRequest, remainingTimeout)
                .compose(response -> processResponse(response, httpRequest))
                .recover(exception -> failResponse(exception, httpRequest));
    }

    private <T> Future<HttpClientResponse> createRequest(HttpRequest<T> httpRequest, long remainingTimeout) {
        final MultiMap requestHeaders = httpRequest.getHeaders();
        final byte[] preparedBody = compressIfRequired(httpRequest.getBody(), requestHeaders);

        return httpClient.request(
                httpRequest.getMethod(),
                httpRequest.getUri(),
                requestHeaders,
                preparedBody,
                remainingTimeout);
    }

    private static byte[] compressIfRequired(byte[] body, MultiMap headers) {
        final String contentEncodingHeader = headers.get(HttpUtil.CONTENT_ENCODING_HEADER);
        return Objects.equals(contentEncodingHeader, HttpHeaderValues.GZIP.toString())
                ? gzip(body)
                : body;
    }

    private static byte[] gzip(byte[] value) {
        try (
                ByteArrayOutputStream obj = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(obj)) {

            gzip.write(value);
            gzip.finish();

            return obj.toByteArray();
        } catch (IOException e) {
            throw new PreBidException("Failed to compress request : " + e.getMessage());
        }
    }

    /**
     * Produces {@link Future} with {@link BidderCall} containing request and error description.
     */
    private static <T> Future<BidderCall<T>> failResponse(Throwable exception, HttpRequest<T> httpRequest) {
        logger.warn("Error occurred while sending HTTP request to a bidder url: {} with message: {}",
                httpRequest.getUri(), exception.getMessage());
        logger.debug("Error occurred while sending HTTP request to a bidder url: {}",
                exception, httpRequest.getUri());

        final BidderError.Type errorType =
                exception instanceof TimeoutException || exception instanceof ConnectTimeoutException
                        ? BidderError.Type.timeout
                        : BidderError.Type.generic;

        return Future.succeededFuture(
                BidderCall.failedHttp(httpRequest, BidderError.create(exception.getMessage(), errorType)));
    }

    /**
     * Produces {@link Future} with {@link BidderCall} containing request, response and possible error description
     * (if status code indicates an error).
     */
    private static <T> Future<BidderCall<T>> processResponse(HttpClientResponse response,
                                                             HttpRequest<T> httpRequest) {

        final int statusCode = response.getStatusCode();
        final HttpResponse httpResponse = HttpResponse.of(statusCode, response.getHeaders(), response.getBody());
        return Future.succeededFuture(BidderCall.succeededHttp(httpRequest, httpResponse, errorOrNull(statusCode)));
    }

    /**
     * Returns {@link BidderError} if HTTP status code is not successful, or null otherwise.
     */
    private static BidderError errorOrNull(int statusCode) {
        if (statusCode != HttpResponseStatus.OK.code() && statusCode != HttpResponseStatus.NO_CONTENT.code()) {
            return BidderError.create(
                    "Unexpected status code: " + statusCode + ". Run with request.test = 1 for more info",
                    statusCode == HttpResponseStatus.BAD_REQUEST.code()
                            ? BidderError.Type.bad_input
                            : BidderError.Type.bad_server_response);
        }
        return null;
    }

    private <T> Void processHttpCall(Bidder<T> bidder,
                                     BidRequest bidRequest,
                                     ResultBuilder<T> seatBidBuilder,
                                     BidderCall<T> httpCall) {

        seatBidBuilder.addHttpCall(httpCall, makeBids(bidder, httpCall, bidRequest));
        return null;
    }

    /**
     * Returns result based on response status code, list of {@link BidderBid}s and other data from bidder.
     */
    private static <T> CompositeBidderResponse makeBids(Bidder<T> bidder,
                                                        BidderCall<T> httpCall,
                                                        BidRequest bidRequest) {

        if (httpCall.getError() != null) {
            return null;
        }

        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return CompositeBidderResponse.empty();
        }
        if (statusCode != HttpResponseStatus.OK.code()) {
            return null;
        }

        return bidder.makeBidderResponse(toHttpCallWithSafeResponseBody(httpCall), bidRequest);
    }

    /**
     * Replaces body of {@link HttpResponse} with empty JSON object if response HTTP status code is equal to 204.
     * <p>
     * Note: this will safe making bids by bidders from JSON parsing error.
     */
    private static <T> BidderCall<T> toHttpCallWithSafeResponseBody(BidderCall<T> httpCall) {
        final HttpResponse response = httpCall.getResponse();
        final int statusCode = response.getStatusCode();

        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            final HttpResponse updatedHttpResponse = HttpResponse.of(statusCode, response.getHeaders(), "{}");
            return BidderCall.succeededHttp(httpCall.getRequest(), updatedHttpResponse, null);
        }

        return httpCall;
    }

    private static class ResultBuilder<T> {

        private final List<HttpRequest<T>> httpRequests;
        private final List<BidderError> previousErrors;
        private final BidderRequestCompletionTracker completionTracker;
        private final BidRejectionTracker bidRejectionTracker;
        private final JacksonMapper mapper;

        private final Map<HttpRequest<T>, BidderCall<T>> bidderCallsRecorded = new HashMap<>();
        private final List<BidderBid> bidsRecorded = new ArrayList<>();
        private final List<BidderError> errorsRecorded = new ArrayList<>();
        private final List<FledgeAuctionConfig> fledgeRecorded = new ArrayList<>();

        ResultBuilder(List<HttpRequest<T>> httpRequests,
                      List<BidderError> previousErrors,
                      BidderRequestCompletionTracker completionTracker,
                      BidRejectionTracker bidRejectionTracker,
                      JacksonMapper mapper) {

            this.httpRequests = httpRequests;
            this.previousErrors = previousErrors;
            this.completionTracker = completionTracker;
            this.bidRejectionTracker = bidRejectionTracker;
            this.mapper = mapper;
        }

        void addHttpCall(BidderCall<T> bidderCall, CompositeBidderResponse bidderResponse) {
            bidderCallsRecorded.put(bidderCall.getRequest(), bidderCall);
            handleBids(bidderResponse);
            handleBidderErrors(bidderResponse);
            handleBidderCallError(bidderCall);
            handleFledgeAuctionConfigs(bidderResponse);
        }

        private void handleBids(CompositeBidderResponse bidderResponse) {
            final List<BidderBid> bids = bidderResponse != null ? bidderResponse.getBids() : null;
            if (bids != null) {
                bidsRecorded.addAll(bids);
                completionTracker.processBids(bids);
                bidRejectionTracker.succeed(bids);
            }
        }

        private void handleBidderErrors(CompositeBidderResponse bidderResponse) {
            final List<BidderError> bidderErrors = bidderResponse != null ? bidderResponse.getErrors() : null;
            if (bidderErrors != null) {
                errorsRecorded.addAll(bidderErrors);
                recordBidderProvidedErrors(bidRejectionTracker, bidderErrors);
            }
        }

        private void handleBidderCallError(BidderCall<T> bidderCall) {
            final BidderError callError = bidderCall.getError();
            final BidderError.Type callErrorType = callError != null ? callError.getType() : null;
            final Set<String> requestedImpIds = bidderCall.getRequest().getImpIds();
            if (callErrorType != null && CollectionUtils.isNotEmpty(requestedImpIds)) {
                bidRejectionTracker.reject(requestedImpIds, BidRejectionReason.fromBidderError(callError));
            }
        }

        private void handleFledgeAuctionConfigs(CompositeBidderResponse bidderResponse) {
            Optional.ofNullable(bidderResponse)
                    .map(CompositeBidderResponse::getFledgeAuctionConfigs)
                    .ifPresent(fledgeRecorded::addAll);
        }

        BidderSeatBid toBidderSeatBid(boolean debugEnabled) {
            final List<BidderCall<T>> httpCalls = new ArrayList<>(bidderCallsRecorded.values());
            httpRequests.stream()
                    .filter(httpRequest -> !bidderCallsRecorded.containsKey(httpRequest))
                    .map(BidderCall::unfinishedHttp)
                    .forEach(httpCalls::add);

            // Capture debugging info from the requests
            final List<ExtHttpCall> extHttpCalls = debugEnabled
                    ? httpCalls.stream().map(this::toExt).toList()
                    : Collections.emptyList();

            final List<BidderError> errors = combineErrors(previousErrors, httpCalls, errorsRecorded);
            return BidderSeatBid.builder()
                    .bids(bidsRecorded)
                    .httpCalls(extHttpCalls)
                    .errors(errors)
                    .fledgeAuctionConfigs(fledgeRecorded)
                    .build();
        }

        /**
         * Constructs {@link ExtHttpCall} filled with HTTP call information.
         */
        private ExtHttpCall toExt(BidderCall<T> httpCall) {
            final HttpRequest<T> request = httpCall.getRequest();
            final BidderCallType callType = httpCall.getCallType();
            final ExtHttpCall.ExtHttpCallBuilder builder = ExtHttpCall.builder()
                    .uri(request.getUri())
                    .calltype(callType != BidderCallType.HTTP ? callType : null)
                    .requestbody(mapper.encodeToString(request.getPayload()))
                    .requestheaders(HttpUtil.toDebugHeaders(request.getHeaders()));

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
        private static <R> List<BidderError> combineErrors(List<BidderError> requestErrors,
                                                           List<BidderCall<R>> calls,
                                                           List<BidderError> responseErrors) {

            return Stream.of(
                            requestErrors.stream(),
                            responseErrors.stream(),
                            calls.stream().map(BidderCall::getError).filter(Objects::nonNull))
                    .flatMap(Function.identity())
                    .toList();
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
            // no need to process bids for no operation tracker
        }
    }
}
