package org.prebid.server.bidder;

import com.iab.openrtb.request.BidRequest;
import io.netty.channel.ConnectTimeoutException;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.auction.model.BidderRequest;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderCall;
import org.prebid.server.bidder.model.BidderCallType;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.proto.openrtb.ext.response.ExtHttpCall;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
                                                 Timeout timeout,
                                                 CaseInsensitiveMultiMap requestHeaders,
                                                 boolean debugEnabled) {

        final BidRequest bidRequest = bidderRequest.getBidRequest();

        final Result<List<HttpRequest<T>>> httpRequestsWithErrors = bidder.makeHttpRequests(bidRequest);
        final List<BidderError> bidderErrors = httpRequestsWithErrors.getErrors();

        final String bidderName = bidderRequest.getBidder();
        final List<HttpRequest<T>> httpRequests = enrichRequests(
                bidderName, httpRequestsWithErrors.getValue(), requestHeaders, bidRequest);

        if (CollectionUtils.isEmpty(httpRequests)) {
            return emptyBidderSeatBidWithErrors(bidderErrors);
        }

        final String storedResponse = bidderRequest.getStoredResponse();

        // stored response available only for single request interaction for the moment.
        final Stream<Future<BidderCall<T>>> httpCalls = isStoredResponse(httpRequests, storedResponse, bidderName)
                ? Stream.of(makeStoredHttpCall(httpRequests.get(0), storedResponse))
                : httpRequests.stream().map(httpRequest -> doRequest(httpRequest, timeout));

        // httpCalls contains recovered and mapped to succeeded Future<BidderHttpCall> with error inside
        final BidderRequestCompletionTracker completionTracker = completionTrackerFactory.create(bidRequest);
        final ResultBuilder<T> resultBuilder =
                new ResultBuilder<>(httpRequests, bidderErrors, completionTracker, mapper);

        final List<Future<Void>> httpRequestFutures = httpCalls
                .map(httpCallFuture -> httpCallFuture
                        .map(httpCall -> bidderErrorNotifier.processTimeout(httpCall, bidder))
                        .map(httpCall -> processHttpCall(bidder, bidRequest, resultBuilder, httpCall)))
                .toList();

        return CompositeFuture.any(
                        CompositeFuture.join(new ArrayList<>(httpRequestFutures)),
                        completionTracker.future())
                .map(ignored -> resultBuilder.toBidderSeatBid(debugEnabled));
    }

    private <T> List<HttpRequest<T>> enrichRequests(String bidderName,
                                                    List<HttpRequest<T>> httpRequests,
                                                    CaseInsensitiveMultiMap requestHeaders,
                                                    BidRequest bidRequest) {

        return httpRequests.stream().map(httpRequest -> httpRequest.toBuilder()
                        .headers(requestEnricher.enrichHeaders(
                                bidderName, httpRequest.getHeaders(), requestHeaders, bidRequest))
                        .build())
                .toList();
    }

    private <T> boolean isStoredResponse(List<HttpRequest<T>> httpRequests, String storedResponse, String bidder) {
        if (StringUtils.isBlank(storedResponse)) {
            return false;
        }

        if (httpRequests.size() > 1) {
            logger.warn("""
                            More than one request was created for stored response, when only single stored response \
                            per bidder is supported for the moment. Request to real {0} bidder will be performed.""",
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

        return Future.succeededFuture(BidderSeatBid.of(
                Collections.emptyList(), Collections.emptyList(), errors, Collections.emptyList()));
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
        logger.warn("Error occurred while sending HTTP request to a bidder url: {0} with message: {1}",
                httpRequest.getUri(), exception.getMessage());
        logger.debug("Error occurred while sending HTTP request to a bidder url: {0}",
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

    private static <T> Result<List<BidderBid>> makeBids(Bidder<T> bidder,
                                                        BidderCall<T> httpCall,
                                                        BidRequest bidRequest) {

        return httpCall.getError() != null
                ? null
                : makeResult(bidder, httpCall, bidRequest);
    }

    /**
     * Returns result based on response status code and list of {@link BidderBid}s from bidder.
     */
    private static <T> Result<List<BidderBid>> makeResult(Bidder<T> bidder,
                                                          BidderCall<T> httpCall,
                                                          BidRequest bidRequest) {

        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.empty();
        }
        if (statusCode != HttpResponseStatus.OK.code()) {
            return null;
        }

        return bidder.makeBids(toHttpCallWithSafeResponseBody(httpCall), bidRequest);
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

        final List<HttpRequest<T>> httpRequests;
        final List<BidderError> previousErrors;
        final BidderRequestCompletionTracker completionTracker;
        private final JacksonMapper mapper;

        final Map<HttpRequest<T>, BidderCall<T>> bidderCallsRecorded = new HashMap<>();
        final List<BidderBid> bidsRecorded = new ArrayList<>();
        final List<BidderError> errorsRecorded = new ArrayList<>();

        ResultBuilder(List<HttpRequest<T>> httpRequests,
                      List<BidderError> previousErrors,
                      BidderRequestCompletionTracker completionTracker,
                      JacksonMapper mapper) {

            this.httpRequests = httpRequests;
            this.previousErrors = previousErrors;
            this.completionTracker = completionTracker;
            this.mapper = mapper;
        }

        void addHttpCall(BidderCall<T> bidderCall, Result<List<BidderBid>> bidsResult) {
            bidderCallsRecorded.put(bidderCall.getRequest(), bidderCall);

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
            return BidderSeatBid.of(bidsRecorded, extHttpCalls, errors, Collections.emptyList());
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
