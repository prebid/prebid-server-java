package org.rtb.vexing;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;

import org.rtb.vexing.handlers.BidRequestHandler;
import org.rtb.vexing.model.request.Bidder;
import org.rtb.vexing.model.request.PreBidRequest;

import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

import static java.util.Objects.isNull;

public class Application extends AbstractVerticle {

    private static final int DEFAULT_PORT = 8080;

    private static final int DEFAULT_POOL_SIZE = 4096;

    private static final int DEFAULT_TIMEOUT_MS = 1000;

    // private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    private String date;

    private HttpClient client;

    private void handleAuction(final RoutingContext context) {
        JsonObject json = context.getBodyAsJson();
        if (isNull(json))
            context.response().setStatusCode(400).end();
        else {
            PreBidRequest bidRequest = json.mapTo(PreBidRequest.class);
            List<Future> futures
                    = bidRequest.adUnits.stream()
                                         .flatMap(unit -> unit.bids.stream().map(bid -> Bidder.from(unit, bid)))
                                         .map(bidder -> BidRequestHandler.clientBid(client, bidder, bidRequest))
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
                                                  Collections.singletonList(BidRequestHandler.NO_BID_RESPONSE)));
                           });
        }
    }

    /** Start the verticle instance. */
    @Override
    public void start() {
        configureJSON();

        // Refresh the date included in the response header every second.
        vertx.setPeriodic(1000, handler -> date = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()));

        client = getClient();

        Router router = appRoutes();
        vertx.createHttpServer()
             .requestHandler(router::accept)
             .listen(config().getInteger("http.port", DEFAULT_PORT));
    }

    /** Create a {@link Router} with all the supported endpoints for this application. */
    private Router appRoutes() {
        Router router = Router.router(getVertx());
        router.route().handler(BodyHandler.create());
        router.post("/auction").handler(this::handleAuction);
        return router;
    }

    /** Configure the {@link Json#mapper} to be used for all JSON serialization. */
    private void configureJSON() {
        Json.mapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false)
                   .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                   .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                   .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                   .registerModule(new AfterburnerModule());
    }

    /** Create a common {@link HttpClient} based upon configuration. */
    private HttpClient getClient() {
        HttpClientOptions options = new HttpClientOptions()
                .setMaxPoolSize(config().getInteger("http-client.max-pool-size", DEFAULT_POOL_SIZE))
                .setConnectTimeout(config().getInteger("http-client.default-timeout-ms", DEFAULT_TIMEOUT_MS));
        return vertx.createHttpClient(options);
    }

    public static void main(String... args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(new Application());
    }
}
