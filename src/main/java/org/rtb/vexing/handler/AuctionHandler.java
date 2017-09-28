package org.rtb.vexing.handler;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
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

import static java.util.Objects.isNull;

public class AuctionHandler {

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
        JsonObject json = context.getBodyAsJson();
        if (isNull(json))
            context.response().setStatusCode(400).end();
        else {
            PreBidRequest bidRequest = json.mapTo(PreBidRequest.class);
            List<Future> futures = bidRequest.adUnits.stream()
                    .flatMap(unit -> unit.bids.stream().map(bid -> Bidder.from(unit, bid)))
                    .map(bidder -> adapters.get(Adapter.Type.valueOf(bidder.bidderCode))
                            .clientBid(httpClient, bidder, bidRequest))
                    .collect(Collectors.toList());

            CompositeFuture.join(futures)
                    .setHandler(response -> {
                        if (response.succeeded())
                            context.response()
                                    .putHeader(HttpHeaders.DATE, date)
                                    .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                                    .end(Json.encode(response.result().list()));
                        else
                            context.response()
                                    .putHeader(HttpHeaders.DATE, date)
                                    .putHeader(HttpHeaders.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                                    .end(Json.encode(
                                            Collections.singletonList(Adapter.NO_BID_RESPONSE)));
                    });
        }
    }
}
