package org.rtb.vexing.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.AsyncResult;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.rtb.vexing.adapter.Adapter;
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.model.request.Bidder;
import org.rtb.vexing.model.request.PreBidRequest;
import org.rtb.vexing.model.response.PreBidResponse;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

public class AuctionHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);

    private final Vertx vertx;
    private final HttpClient httpClient;

    private final AdapterCatalog adapters;

    private String date;

    public AuctionHandler(HttpClient httpClient, AdapterCatalog adapters, Vertx vertx) {
        this.httpClient = httpClient;
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
            final List<Bidder> bidders = preBidRequest.adUnits.stream()
                    .flatMap(unit -> unit.bids.stream().map(bid -> Bidder.from(unit, bid)))
                    .collect(Collectors.toList());
            final List<Future> futures = bidders.stream()
                    .map(bidder -> adapters.get(bidder.bidderCode).clientBid(bidder, preBidRequest))
                    .collect(Collectors.toList());

            // FIXME: are we tolerating individual exchange failures?
            CompositeFuture.join(futures)
                    .setHandler(bidResponsesResult ->
                            respondWith(bidResponseOrNoBid(bidResponsesResult, preBidRequest, bidders), context));
        }
    }

    private void respondWith(PreBidResponse response, RoutingContext context) {
        context.response()
                .putHeader(HttpHeaders.DATE, date)
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .end(Json.encode(response));
    }

    private static PreBidResponse bidResponseOrNoBid(AsyncResult<CompositeFuture> bidResponsesResult,
                                                     PreBidRequest preBidRequest, List<Bidder> reqBidders) {
        return bidResponsesResult.succeeded()
                ? PreBidResponse.builder()
                .status("OK") // FIXME
                .tid(preBidRequest.tid)
                .bidderStatus(reqBidders.stream().map(b -> org.rtb.vexing.model.response.Bidder.builder()
                        .bidder(b.bidderCode)
                        .numBids(1) // FIXME
                        .responseTime(10) // FIXME
                        .build())
                        .collect(Collectors.toList()))
                .bids(bidResponsesResult.result().list())
                .build()
                : Adapter.NO_BID_RESPONSE;
    }
}
