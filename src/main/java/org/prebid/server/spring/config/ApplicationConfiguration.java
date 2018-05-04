package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.file.FileSystem;
import io.vertx.core.spi.VerticleFactory;
import io.vertx.ext.web.Router;
import org.prebid.server.PrebidVerticle;
import org.prebid.server.json.ObjectMapperConfigurer;
import org.prebid.server.vertx.JdbcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;

@Configuration
public class ApplicationConfiguration {

    static {
        ObjectMapperConfigurer.configure();
    }

    @Bean
    ConversionService conversionService() {
        return new DefaultConversionService();
    }

    @Bean
    Vertx vertx(@Value("${vertx.worker-pool-size}") Integer workerPoolSize, VerticleFactory verticleFactory) {
        final Vertx vertx = Vertx.vertx(new VertxOptions().setWorkerPoolSize(workerPoolSize));
        vertx.registerVerticleFactory(verticleFactory);
        return vertx;
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
}
