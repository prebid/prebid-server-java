package org.rtb.vexing.handler.openrtb2;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.rtb.vexing.auction.ExchangeService;
import org.rtb.vexing.validation.RequestValidator;
import org.rtb.vexing.validation.ValidationResult;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AuctionHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);

    private final long maxRequestSize;
    private final RequestValidator requestValidator;
    private final ExchangeService exchangeService;

    AuctionHandler(long maxRequestSize, RequestValidator requestValidator, ExchangeService exchangeService) {
        this.maxRequestSize = maxRequestSize;
        this.requestValidator = Objects.requireNonNull(requestValidator);
        this.exchangeService = Objects.requireNonNull(exchangeService);
    }

    @Override
    public void handle(RoutingContext context) {
        parseRequest(context)
                .compose(AuctionHandler::processStoredRequests)
                .map(AuctionHandler::populateDerivedFields)
                .compose(this::validateRequest)
                .compose(exchangeService::holdAuction)
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

    private static Future<BidRequest> processStoredRequests(BidRequest bidRequest) {
        // TODO: implement stored requests processing
        return Future.succeededFuture(bidRequest);
    }

    private static BidRequest populateDerivedFields(BidRequest bidRequest) {
        // TODO: implement derived fields population
        return bidRequest;
    }

    private Future<BidRequest> validateRequest(BidRequest bidRequest) {
        final Future<BidRequest> result;
        final ValidationResult validationResult = requestValidator.validate(bidRequest);
        if (validationResult.hasErrors()) {
            result = Future.failedFuture(new InvalidRequestException(validationResult.errors));
        } else {
            result = Future.succeededFuture(bidRequest);
        }

        return result;
    }

    private void handleResult(AsyncResult<BidResponse> responseResult, RoutingContext context) {
        if (responseResult.succeeded()) {
            context.response().end(Json.encode(responseResult.result()));
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

    private static class InvalidRequestException extends RuntimeException {
        private final List<String> messages;

        InvalidRequestException(String message) {
            this.messages = Collections.singletonList(message);
        }

        InvalidRequestException(List<String> messages) {
            this.messages = messages;
        }

        List<String> getMessages() {
            return messages;
        }
    }
}
