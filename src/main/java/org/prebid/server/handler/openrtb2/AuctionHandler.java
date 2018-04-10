package org.prebid.server.handler.openrtb2;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.prebid.server.auction.AuctionRequestFactory;
import org.prebid.server.auction.ExchangeService;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.InvalidRequestException;
import org.prebid.server.execution.Timeout;
import org.prebid.server.execution.TimeoutFactory;
import org.prebid.server.handler.AbstractMeteredHandler;
import org.prebid.server.metric.prebid.RequestHandlerMetrics;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AuctionHandler extends AbstractMeteredHandler<RequestHandlerMetrics> {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);

    private final long defaultTimeout;
    private final ExchangeService exchangeService;
    private final UidsCookieService uidsCookieService;
    private final AuctionRequestFactory auctionRequestFactory;

    public AuctionHandler(long defaultTimeout, ExchangeService exchangeService,
                          AuctionRequestFactory auctionRequestFactory, UidsCookieService uidsCookieService,
                          RequestHandlerMetrics handlerMetrics, Clock clock, TimeoutFactory timeoutFactory) {
        super(handlerMetrics, clock, timeoutFactory);
        this.defaultTimeout = defaultTimeout;
        this.exchangeService = Objects.requireNonNull(exchangeService);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.auctionRequestFactory = Objects.requireNonNull(auctionRequestFactory);
    }

    @Override
    public void handle(RoutingContext context) {
        long startTime = getClock().millis();
        getHandlerMetrics().updateRequestMetrics(context, this);

        UidsCookie uidsCookie = getUidsCookie(context);

        auctionRequestFactory.fromRequest(context)
                .recover(th -> getHandlerMetrics().updateErrorRequestsMetric(context, this, th))
                .map(bidRequest -> getHandlerMetrics().updateAppAndNoCookieMetrics(context, this, bidRequest,
                        uidsCookie.hasLiveUids(), bidRequest.getApp() != null))
                .compose(bidRequest -> exchangeService.holdAuction(bidRequest, uidsCookie,
                        timeout(startTime, bidRequest)))
                .setHandler(responseResult -> handleResult(responseResult, context));
    }

    private Timeout timeout(long startTime, BidRequest bidRequest) {
        return super.timeout(startTime, bidRequest.getTmax(), defaultTimeout);
    }

    private UidsCookie getUidsCookie(RoutingContext context) {
        return uidsCookieService.parseFromRequest(context);
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
