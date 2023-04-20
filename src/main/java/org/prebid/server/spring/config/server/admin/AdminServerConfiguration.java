package org.prebid.server.spring.config.server.admin;

import io.vertx.core.Vertx;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.prebid.server.vertx.verticles.VerticleDefinition;
import org.prebid.server.vertx.verticles.server.ServerVerticle;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Supplier;

@Configuration
@ConditionalOnProperty(prefix = "admin", name = "port")
public class AdminServerConfiguration {

    @Bean("adminPortAdminServerRouterFactory")
    Supplier<Router> adminPortAdminServerRouterFactory(
            Vertx vertx,
            @Qualifier("adminPortAdminResourcesBinder") AdminResourcesBinder adminResourcesBinder,
            BodyHandler bodyHandler) {

        return () -> {
            final Router router = Router.router(vertx);
            router.route().handler(bodyHandler);

            adminResourcesBinder.bind(router);
            return router;
        };
    }

    @Bean
    VerticleDefinition adminPortAdminHttpServerVerticleDefinition(
            @Qualifier("adminPortAdminServerRouterFactory") Supplier<Router> routerFactory,
            @Value("${admin.port}") int port) {

        return VerticleDefinition.ofSingleInstance(
                () -> new ServerVerticle(
                        "Admin Http Server",
                        SocketAddress.inetSocketAddress(port, "0.0.0.0"),
                        routerFactory));
    }
}
