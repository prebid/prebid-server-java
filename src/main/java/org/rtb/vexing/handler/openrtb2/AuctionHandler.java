package org.rtb.vexing.handler.openrtb2;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.rtb.vexing.auction.ExchangeService;
import org.rtb.vexing.auction.PreBidRequestContextFactory;
import org.rtb.vexing.auction.StoredRequestProcessor;
import org.rtb.vexing.cookie.UidsCookieService;
import org.rtb.vexing.exception.InvalidRequestException;
import org.rtb.vexing.execution.GlobalTimeout;
import org.rtb.vexing.validation.RequestValidator;
import org.rtb.vexing.validation.ValidationResult;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AuctionHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);

    private static final Clock CLOCK = Clock.systemDefaultZone();

    private final long maxRequestSize;
    private final long defaultTimeout;
    private final RequestValidator requestValidator;
    private final ExchangeService exchangeService;
    private final StoredRequestProcessor storedRequestProcessor;
    private final PreBidRequestContextFactory preBidRequestContextFactory;
    private final UidsCookieService uidsCookieService;

    public AuctionHandler(long maxRequestSize, long defaultTimeout, RequestValidator requestValidator,
                          ExchangeService exchangeService, StoredRequestProcessor storedRequestProcessor,
                          PreBidRequestContextFactory preBidRequestContextFactory,
                          UidsCookieService uidsCookieService) {
        this.maxRequestSize = maxRequestSize;
        this.defaultTimeout = defaultTimeout;
        this.requestValidator = Objects.requireNonNull(requestValidator);
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.storedRequestProcessor = Objects.requireNonNull(storedRequestProcessor);
        this.preBidRequestContextFactory = Objects.requireNonNull(preBidRequestContextFactory);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
    }

    @Override
    public void handle(RoutingContext context) {
        // Prebid Server interprets request.tmax to be the maximum amount of time that a caller is willing to wait
        // for bids. However, tmax may be defined in the Stored Request data.
        // If so, then the trip to the backend might use a significant amount of this time. We can respect timeouts
        // more accurately if we note the real start time, and use it to compute the auction timeout.
        final long startTime = CLOCK.millis();

        parseRequest(context)
                .compose(storedRequestProcessor::processStoredRequests)
                .map(bidRequest -> preBidRequestContextFactory.fromRequest(bidRequest, context))
                .map(this::validateRequest)
                .compose(bidRequest ->
                        exchangeService.holdAuction(bidRequest, uidsCookieService.parseFromRequest(context),
                                timeout(bidRequest, startTime)))
                .setHandler(responseResult -> handleResult(responseResult, context));
    }

    private Future<BidRequest> parseRequest(RoutingContext context) {
        Future<BidRequest> result;

        final Buffer body = context.getBody();
        if (body == null) {
            result = Future.failedFuture(new InvalidRequestException("Incoming request has no body"));
        } else if (body.length() > maxRequestSize) {
            result = Future.failedFuture(new InvalidRequestException(
                    String.format("Request size exceeded max size of %d bytes.", maxRequestSize)));
        } else {
            try {
                result = Future.succeededFuture(new JsonObject(body.toString()).mapTo(BidRequest.class));
            } catch (DecodeException e) {
                result = Future.failedFuture(new InvalidRequestException(e.getMessage()));
            }
        }

        return result;
    }

    private BidRequest validateRequest(BidRequest bidRequest) {
        final ValidationResult validationResult = requestValidator.validate(bidRequest);
        if (validationResult.hasErrors()) {
            throw new InvalidRequestException(validationResult.errors);
        }
        return bidRequest;
    }

    private GlobalTimeout timeout(BidRequest bidRequest, long startTime) {
        final Long tmax = bidRequest.getTmax();
        return GlobalTimeout.create(startTime, tmax != null && tmax > 0 ? tmax : defaultTimeout);
    }

    private void handleResult(AsyncResult<BidResponse> responseResult, RoutingContext context) {
        if (responseResult.succeeded()) {
            context.response()
                    .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                    .end(Json.encode(responseResult.result()));
        } else {
            final Throwable exception = responseResult.cause();
            if (exception instanceof InvalidRequestException) {
                final List<String> messages = ((InvalidRequestException) exception).getMessages();
                logger.info("Invalid request format: {0}", messages);
                context.response()
                        .setStatusCode(HttpResponseStatus.BAD_REQUEST.code())
                        .end(messages.stream().map(m -> String.format("Invalid request format: %s", m))
                                .collect(Collectors.joining("\n")));
            } else {
                logger.error("Critical error while running the auction", exception);
                context.response()
                        .setStatusCode(HttpResponseStatus.INTERNAL_SERVER_ERROR.code())
                        .end(String.format("Critical error while running the auction: %s", exception.getMessage()));
            }
        }
    }
}
