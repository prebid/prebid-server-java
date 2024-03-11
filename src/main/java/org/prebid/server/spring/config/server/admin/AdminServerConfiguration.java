package org.prebid.server.spring.config.server.admin;

import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.prebid.server.vertx.verticles.VerticleDefinition;
import org.prebid.server.vertx.verticles.server.ServerVerticle;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "admin", name = "port")
public class AdminServerConfiguration {

    @Bean
    Router adminPortAdminServerRouter(Vertx vertx,
                                      AdminResourcesBinder adminPortAdminResourcesBinder,
                                      BodyHandler bodyHandler) {

        final Router router = Router.router(vertx);
        router.route().handler(bodyHandler);

        adminPortAdminResourcesBinder.bind(router);
        return router;
    }

    @Bean
    VerticleDefinition adminPortAdminHttpServerVerticleDefinition(Router adminPortAdminServerRouter,
                                                                  @Value("${admin.port}") int port) {

        return VerticleDefinition.ofSingleInstance(
                () -> new ServerVerticle(
                        "Admin Http Server",
                        SocketAddress.inetSocketAddress(port, "0.0.0.0"),
                        adminPortAdminServerRouter));
    }
}
