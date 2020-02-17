package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.prebid.server.handler.CustomizedAdminEndpoint;
import org.prebid.server.handler.VersionHandler;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.vertx.ContextRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "admin", name = "port")
public class AdminServerConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(AdminServerConfiguration.class);

    @Autowired
    private ContextRunner contextRunner;

    @Autowired
    private Vertx vertx;

    @Autowired
    @Qualifier("adminRouter")
    private Router adminRouter;

    @Value("${admin.port}")
    private int adminPort;

    @Bean
    VersionHandler versionHandler(JacksonMapper mapper) {
        return VersionHandler.create("git-revision.json", mapper);
    }

    @Bean(name = "adminRouter")
    Router adminRouter(BodyHandler bodyHandler, VersionHandler versionHandler,
                       List<CustomizedAdminEndpoint> customizedAdminEndpoints) {
        final Router router = Router.router(vertx);
        router.route().handler(bodyHandler);
        router.route("/version").handler(versionHandler);

        customizedAdminEndpoints.stream()
                .filter(customizedAdminEndpoint -> !customizedAdminEndpoint.isOnApplicationPort())
                .forEach(customizedAdminEndpoint -> customizedAdminEndpoint.router(router));

        return router;
    }

    @PostConstruct
    public void startAdminServer() {
        logger.info("Starting Admin Server to serve requests on port {0,number,#}", adminPort);

        contextRunner.<HttpServer>runOnServiceContext(future ->
                vertx.createHttpServer().requestHandler(adminRouter).listen(adminPort, future));

        logger.info("Successfully started Admin Server");
    }
}
