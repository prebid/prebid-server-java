package org.rtb.vexing.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.ext.web.Router;
import org.rtb.vexing.PrebidVerticle;
import org.rtb.vexing.settings.ApplicationSettings;
import org.rtb.vexing.settings.StoredRequestFetcher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class ApplicationConfiguration {

    @Bean
    Vertx vertx() {
        return Vertx.vertx();
    }

    @Bean
    FileSystem fileSystem(Vertx vertx) {
        return vertx.fileSystem();
    }

    @Bean
    SpringVerticleFactory springVerticleFactory() {
        return new SpringVerticleFactory();
    }

    @Bean
    @Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
    PrebidVerticle prebidVerticle(
            @Value("${http.port}") int port,
            Vertx vertx,
            Router router,
            ApplicationSettings applicationSettings,
            StoredRequestFetcher storedRequestFetcher) {

        return new PrebidVerticle(vertx, router, applicationSettings, storedRequestFetcher, port);
    }
}
