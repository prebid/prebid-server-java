package org.prebid.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystem;
import io.vertx.ext.web.Router;
import org.prebid.PrebidVerticle;
import org.prebid.json.ObjectMapperConfigurer;
import org.prebid.vertx.JdbcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import javax.annotation.PostConstruct;

@Configuration
public class ApplicationConfiguration {

    @Bean
    Vertx vertx(@Value("${vertx.worker-pool-size}") Integer workerPoolSize) {
        return Vertx.vertx(new VertxOptions().setWorkerPoolSize(workerPoolSize));
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
            @Autowired(required = false) JdbcClient jdbcClient) {

        return new PrebidVerticle(vertx, router, jdbcClient, port);
    }

    @PostConstruct
    void initializeObjectMapper() {
        ObjectMapperConfigurer.configure();
    }
}
