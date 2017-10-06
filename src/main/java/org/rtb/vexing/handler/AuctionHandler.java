package org.rtb.vexing.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.model.AdUnitBid;
import org.rtb.vexing.model.Bidder;
import org.rtb.vexing.model.BidderResult;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.Bid;
import org.rtb.vexing.model.response.BidderStatus;
import org.rtb.vexing.model.response.PreBidResponse;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class AuctionHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);

    private final Vertx vertx;

    private final AdapterCatalog adapters;

    private String date;

    public AuctionHandler(AdapterCatalog adapters, Vertx vertx) {
        this.adapters = adapters;
        this.vertx = vertx;

        // Refresh the date included in the response header every second.
        this.vertx.setPeriodic(1000,
                handler -> date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));
    }

    /**
     * Auction handler will resolve all bidders in the incoming ad request, issue the request to the different
     * clients, then return an array of the responses.
     */
    public void auction(RoutingContext context) {
        final JsonObject json = context.getBodyAsJson();
        if (json == null) {
            logger.error("Incoming request has no body.");
            context.response().setStatusCode(400).end();
        } else {
            final PreBidRequest preBidRequest = json.mapTo(PreBidRequest.class);
            final List<Bidder> bidders = extractBidders(preBidRequest);
            final List<Future> bidderResponseFutures = bidders.stream()
                    .map(bidder -> adapters.get(bidder.bidderCode)
                            .requestBids(bidder, preBidRequest, context.request()))
                    .collect(Collectors.toList());

            // FIXME: are we tolerating individual exchange failures?
            CompositeFuture.join(bidderResponseFutures)
                    .setHandler(bidderResponsesResult ->
                            respondWith(bidResponsesOrNoBid(bidderResponsesResult, preBidRequest), context));
        }
    }

    private static List<Bidder> extractBidders(PreBidRequest preBidRequest) {
        return preBidRequest.adUnits.stream()
                .flatMap(unit -> unit.bids.stream().map(bid -> AdUnitBid.from(unit, bid)))
                .collect(Collectors.groupingBy(a -> a.bidderCode))
                .entrySet().stream()
                .map(e -> Bidder.from(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private void respondWith(PreBidResponse response, RoutingContext context) {
        context.response()
                .putHeader(HttpHeaders.DATE, date)
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .end(Json.encode(response));
    }

    private static PreBidResponse bidResponsesOrNoBid(AsyncResult<CompositeFuture> bidResponsesResult,
                                                      PreBidRequest preBidRequest) {
        return bidResponsesResult.succeeded()
                ? toPrebidResponse(bidResponsesResult.result().list(), preBidRequest)
                : Adapter.NO_BID_RESPONSE;
    }

    private static PreBidResponse toPrebidResponse(List<BidderResult> bidderResults, PreBidRequest preBidRequest) {
        final List<BidderStatus> bidderStatuses = bidderResults.stream()
                .map(br -> br.bidderStatus)
                .collect(Collectors.toList());
        final List<Bid> bids = bidderResults.stream()
                .flatMap(br -> br.bids.stream())
                .collect(Collectors.toList());
        return PreBidResponse.builder()
                .status("OK") // FIXME: might be "no_cookie"
                .tid(preBidRequest.tid)
                .bidderStatus(bidderStatuses)
                .bids(bids)
                .build();
    }
}
