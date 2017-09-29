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
import org.rtb.vexing.adapter.RubiconAdapter;
import org.rtb.vexing.model.request.Bidder;
import org.rtb.vexing.model.request.PreBidRequest;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.stream.Collectors;

public class AuctionHandler {

    private static final Logger logger = LoggerFactory.getLogger(AuctionHandler.class);

    private final Vertx vertx;
    private final HttpClient httpClient;

    private final EnumMap<Adapter.Type, Adapter> adapters;

    private String date;

    public AuctionHandler(HttpClient httpClient, Vertx vertx) {
        this.httpClient = httpClient;
        this.vertx = vertx;

        adapters = new EnumMap<>(Adapter.Type.class);
        adapters.put(Adapter.Type.rubicon, new RubiconAdapter(vertx.getOrCreateContext().config()
                .getJsonObject("adapters").getJsonObject("rubicon")));

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
            final List<Future> futures = preBidRequest.adUnits.stream()
                    .flatMap(unit -> unit.bids.stream().map(bid -> Bidder.from(unit, bid)))
                    .map(bidder -> adapters.get(Adapter.Type.valueOf(bidder.bidderCode))
                            .clientBid(httpClient, bidder, preBidRequest))
                    .collect(Collectors.toList());

            // FIXME: are we tolerating individual exchange failures?
            CompositeFuture.join(futures)
                    .setHandler(bidResponsesResult -> respondWith(bidResponsesOrNoBid(bidResponsesResult), context));
        }
    }

    private void respondWith(List<?> body, RoutingContext context) {
        context.response()
                .putHeader(HttpHeaders.DATE, date)
                .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                .end(Json.encode(body));
    }

    private static List<?> bidResponsesOrNoBid(AsyncResult<CompositeFuture> bidResponsesResult) {
        return bidResponsesResult.succeeded()
                ? bidResponsesResult.result().list()
                : Collections.singletonList(Adapter.NO_BID_RESPONSE);
    }
}
