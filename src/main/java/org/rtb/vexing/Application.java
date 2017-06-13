package org.rtb.vexing;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import com.iab.openrtb.response.BidResponse;

import org.rtb.vexing.model.request.Bidder;
import org.rtb.vexing.model.request.PreBidRequest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import static io.netty.handler.codec.http.HttpHeaderValues.APPLICATION_JSON;
import static java.util.Objects.isNull;

public class Application extends AbstractVerticle {

    private static final int PORT = 8080;
//    private static final int PORT = 9090;

    static final BidResponse NO_BID_RESPONSE = BidResponse.builder().nbr(0).build();

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private String date;

    private HttpClient client;

    private void handleAuction(final RoutingContext context) {
        JsonObject json = context.getBodyAsJson();
        if (isNull(json))
            context.response().setStatusCode(400).end();
        else {
            PreBidRequest bidRequest = json.mapTo(PreBidRequest.class);
            List<Future> futures
                    = bidRequest.ad_units.stream()
                                         .flatMap(unit -> unit.bids.stream().map(bid -> Bidder.from(unit, bid)))
                                         .map(bidder -> BidRequestHandler.clientBid(client, bidder, bidRequest))
                                         .collect(Collectors.toList());

            CompositeFuture.join(futures)
                           .setHandler(response -> {
                               if (response.succeeded())
                                   context.response()
                                          .putHeader(HttpHeaders.DATE, date)
                                          .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                                          .end(Json.encode(response.result().list()));
                               else
                                   context.response()
                                          .putHeader(HttpHeaders.DATE, date)
                                          .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                                          .end(Json.encode(Collections.singletonList(NO_BID_RESPONSE)));
                           });
        }
    }

    @Override
    public void start() {
        configureJSON();

        // Refresh the date included in the response header every second.
        vertx.setPeriodic(1000, handler -> date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));

        client = getClient();

        Router router = appRoutes();
        vertx.createHttpServer()
             .requestHandler(router::accept)
             .listen(PORT);

        logger.info("Vexing Application started on port " + PORT + '.');
    }

    private Router appRoutes() {
        Router router = Router.router(getVertx());
        router.route().handler(BodyHandler.create());
        router.post("/auction").handler(this::handleAuction);
        return router;
    }

    private void configureJSON() {
        Json.mapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false)
                   .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                   .registerModule(new AfterburnerModule());
    }

    private HttpClient getClient() {
        HttpClientOptions options = new HttpClientOptions().setMaxPoolSize(4000);
        return vertx.createHttpClient(options);
    }

    public static void main(String... args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new Application());
    }
}
