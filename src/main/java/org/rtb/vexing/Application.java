package org.rtb.vexing;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.response.BidResponse;

import org.rtb.vexing.model.request.Bidder;
import org.rtb.vexing.model.request.PreBidRequest;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientResponse;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import static java.util.Objects.isNull;

public class Application extends AbstractVerticle {

    private static final int PORT = 8080;
//    private static final int PORT = 9090;

    private static final BidResponse NO_BID_RESPONSE = BidResponse.builder().nbr(0).build();
    private static final String APPLICATION_JSON = "application/json";

    private Logger logger = LoggerFactory.getLogger(Application.class);

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
                                         .map(bidder -> clientBid(bidder, bidRequest))
                                         .collect(Collectors.toList());

            CompositeFuture.join(futures)
                           .setHandler(response -> {
                               HttpServerResponse serverResponse
                                       = context.response()
                                                .putHeader(HttpHeaders.DATE, date)
                                                .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON);
                               if (response.succeeded())
                                   serverResponse.end(Json.encode(response.result().list()));
                               else
                                   serverResponse.end(Json.encode(Collections.singletonList(NO_BID_RESPONSE)));
                           });
        }
    }

    private Future clientBid(Bidder bidder, PreBidRequest request) {
        Imp imp = Imp.builder()
                     .id(bidder.bid_id)
                     .banner(Banner.builder().format(request.ad_units.get(0).sizes).build())
                     .build();
        BidRequest bidRequest = BidRequest.builder()
                                          .id(bidder.bid_id)
                                          .app(request.app)
                                          .device(request.device)
                                          .imp(Collections.singletonList(imp))
                                          .build();

        Future<BidResponse> future = Future.future();
        try {
            URL url = new URL(bidder.bidder_code);
            client.post(url.getPort(), url.getHost(), url.getFile(), clientResponseHandler(future))
                  .putHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_JSON)
                  .setTimeout(request.timeout_millis)
                  .exceptionHandler(throwable -> {
                      if (!(throwable instanceof TimeoutException))
                          logger.warn("Got exception: " + throwable.getMessage() + " " + throwable.getClass().getName());

                      future.complete(NO_BID_RESPONSE);
                  })
                  .end(Json.encode(bidRequest));
        } catch (MalformedURLException e) {
            logger.warn("Cannot parse URL: " + bidder.bidder_code);
            future.complete(NO_BID_RESPONSE);
        }
        return future;
    }

    private Handler<HttpClientResponse> clientResponseHandler(Future<BidResponse> future) {
        return response -> {
            if (response.statusCode() == 200)
                response.bodyHandler(buffer -> future.complete(Json.decodeValue(buffer.toString(), BidResponse.class)));
            else
                future.complete(NO_BID_RESPONSE);
        };
    }

    private HttpClient getClient() {
        HttpClientOptions options = new HttpClientOptions().setMaxPoolSize(4000);
        return vertx.createHttpClient(options);
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

    public static void main(String... args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new Application());
    }
}
