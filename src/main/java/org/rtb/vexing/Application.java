package org.rtb.vexing;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixList;
import de.malkusch.whoisServerList.publicSuffixList.PublicSuffixListFactory;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.Json;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CookieHandler;
import org.rtb.vexing.adapter.AdapterCatalog;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.handler.AuctionHandler;
import org.rtb.vexing.json.ObjectMapperConfigurer;
import org.rtb.vexing.settings.ApplicationSettings;

import java.io.IOException;
import java.util.Properties;

public class Application extends AbstractVerticle {

    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_POOL_SIZE = 4096;
    private static final int DEFAULT_TIMEOUT_MS = 1000;

    private static final Logger logger = LoggerFactory.getLogger(Application.class);

    private ApplicationSettings applicationSettings;

    private AdapterCatalog adapterCatalog;

    private HttpClient httpClient;

    /**
     * Start the verticle instance.
     */
    @Override
    public void start(Future<Void> startFuture) {
        ApplicationConfig.create(vertx, "/default-conf.json")
                .compose(this::initialize)
                .compose(
                        httpServer -> {
                            logger.debug("Vexing server has been started successfully");
                            startFuture.complete();
                        },
                        startFuture);
    }

    private Future<HttpServer> initialize(ApplicationConfig config) {
        applicationSettings = ApplicationSettings.create(vertx, config);

        configureJSON();

        final PublicSuffixList suffixList = psl();

        httpClient = httpClient(config);

        adapterCatalog = new AdapterCatalog(config, httpClient, suffixList);

        final Router router = routes();

        final Future<HttpServer> httpServerFuture = Future.future();
        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(config.getInteger("http.port", DEFAULT_PORT), httpServerFuture);

        return httpServerFuture;
    }

    /**
     * Configure the {@link Json#mapper} to be used for all JSON serialization.
     */
    private void configureJSON() {
        ObjectMapperConfigurer.configure();
        // FIXME: remove
        Json.prettyMapper.configure(MapperFeature.DEFAULT_VIEW_INCLUSION, false)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
                .setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .registerModule(new AfterburnerModule());
    }

    /**
     * Create a common {@link HttpClient} based upon configuration.
     */
    private HttpClient httpClient(ApplicationConfig config) {
        final HttpClientOptions options = new HttpClientOptions()
                .setMaxPoolSize(config.getInteger("http-client.max-pool-size", DEFAULT_POOL_SIZE))
                .setConnectTimeout(config.getInteger("http-client.default-timeout-ms", DEFAULT_TIMEOUT_MS));
        return vertx.createHttpClient(options);
    }

    /**
     * Initialize Public Suffix List library based on the shipped list of effective TLD names.
     */
    private static PublicSuffixList psl() {
        final PublicSuffixListFactory factory = new PublicSuffixListFactory();

        Properties properties = factory.getDefaults();
        properties.setProperty(PublicSuffixListFactory.PROPERTY_LIST_FILE, "/effective_tld_names.dat");
        try {
            return factory.build(properties);
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalArgumentException("Could not initialize public suffix list", e);
        }
    }

    /**
     * Create a {@link Router} with all the supported endpoints for this application.
     */
    private Router routes() {
        final Router router = Router.router(getVertx());
        router.route().handler(CookieHandler.create());
        router.route().handler(BodyHandler.create());
        router.post("/auction").handler(new AuctionHandler(applicationSettings, adapterCatalog, vertx)::auction);
        return router;
    }
}
