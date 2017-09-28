package org.rtb.vexing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.json.Json;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.rtb.vexing.handlers.AuctionHandler;

public class Application extends AbstractVerticle {

    private static final int DEFAULT_PORT = 8080;

    private static final int DEFAULT_POOL_SIZE = 4096;

    private static final int DEFAULT_TIMEOUT_MS = 1000;

    // private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    private HttpClient httpClient;

    /**
     * Start the verticle instance.
     */
    @Override
    public void start() {
        configureJSON();

        httpClient = httpClient();

        final Router router = routes();

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(config().getInteger("http.port", DEFAULT_PORT));
    }

    /**
     * Configure the {@link Json#mapper} to be used for all JSON serialization.
     */
    private void configureJSON() {
        Json.mapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .registerModule(new AfterburnerModule());
    }

    /**
     * Create a common {@link HttpClient} based upon configuration.
     */
    private HttpClient httpClient() {
        final HttpClientOptions options = new HttpClientOptions()
                .setMaxPoolSize(config().getInteger("http-client.max-pool-size", DEFAULT_POOL_SIZE))
                .setConnectTimeout(config().getInteger("http-client.default-timeout-ms", DEFAULT_TIMEOUT_MS));
        return vertx.createHttpClient(options);
    }

    /**
     * Create a {@link Router} with all the supported endpoints for this application.
     */
    private Router routes() {
        final Router router = Router.router(getVertx());
        router.route().handler(BodyHandler.create());
        router.post("/auction").handler(new AuctionHandler(httpClient, vertx)::auction);
        return router;
    }
}
