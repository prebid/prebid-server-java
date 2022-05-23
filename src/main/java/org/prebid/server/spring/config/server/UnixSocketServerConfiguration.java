package org.prebid.server.spring.config.server;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Router;
import org.prebid.server.handler.ExceptionHandler;
import org.prebid.server.vertx.ContextRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

@Configuration
@ConditionalOnProperty(name = "server.unix-socket.enabled", havingValue = "true")
public class UnixSocketServerConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(UnixSocketServerConfiguration.class);

    @Autowired
    private ContextRunner contextRunner;

    @Autowired
    private Vertx vertx;

    @Autowired
    private HttpServerOptions httpServerOptions;

    @Autowired
    private ExceptionHandler exceptionHandler;

    @Autowired
    @Qualifier("router")
    private Router router;

    @Value("${server.unix-socket.path}")
    private String socketPath;

    @Value("${server.unix-socket.server-instances}")
    private Integer serverNum;

    @PostConstruct
    public void startUnixSocketServer() {
        logger.info(
                "Starting {0} instances of Unix Socket Server to serve requests on socket {1}",
                serverNum,
                socketPath);

        contextRunner.<HttpServer>runOnNewContext(serverNum, promise ->
                vertx.createHttpServer(httpServerOptions)
                        .exceptionHandler(exceptionHandler)
                        .requestHandler(router)
                        .listen(SocketAddress.domainSocketAddress(socketPath), promise));

        logger.info("Successfully started {0} instances of Unix Socket Server", serverNum);
    }
}
