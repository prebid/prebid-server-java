package org.prebid.server.spring.config.server;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Router;
import lombok.Data;
import org.prebid.server.handler.ExceptionHandler;
import org.prebid.server.vertx.ContextRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.validation.constraints.NotBlank;

@Configuration
@ConditionalOnBean(UnixSocketServerConfiguration.UnixSocketServerConfigurationProperties.class)
public class UnixSocketServerConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(UnixSocketServerConfiguration.class);

    @Autowired
    private ContextRunner contextRunner;

    @Value("${vertx.http-server-instances}")
    private int httpServerNum;

    @Autowired
    private Vertx vertx;

    @Autowired
    private HttpServerOptions httpServerOptions;

    @Autowired
    private ExceptionHandler exceptionHandler;

    @Autowired
    @Qualifier("router")
    private Router router;

    @Autowired
    private UnixSocketServerConfigurationProperties socketConfigurationProperties;

    @PostConstruct
    public void startUnixSocketServer() {
        logger.info(
                "Starting {0} instances of Unix Socket Server to serve requests on socket {1}",
                httpServerNum,
                socketConfigurationProperties.getPath());

        contextRunner.<HttpServer>runOnNewContext(httpServerNum, promise ->
                vertx.createHttpServer(httpServerOptions)
                        .exceptionHandler(exceptionHandler)
                        .requestHandler(router)
                        .listen(SocketAddress.domainSocketAddress(socketConfigurationProperties.getPath()), promise));

        logger.info("Successfully started {0} instances of Unix Socket Server", httpServerNum);
    }

    @Component
    @ConditionalOnProperty(name = "server.unix-domain-socket.enabled", havingValue = "true")
    @ConfigurationProperties(prefix = "server.unix-domain-socket")
    @Data
    @Validated
    public static class UnixSocketServerConfigurationProperties {

        @NotBlank
        private String path;
    }
}
