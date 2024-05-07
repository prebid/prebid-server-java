package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisRetryConfig;

public class RedisClient {

    private static final Logger logger = LoggerFactory.getLogger(RedisClient.class);

    private final RedisOptions options;

    private RedisConnection connection;

    private RedisAPI redisAPI;

    private final RedisRetryConfig retryConfig;

    private final Vertx vertx;

    private final String type;

    public RedisClient(
            Vertx vertx,
            String host,
            int port,
            String password,
            RedisRetryConfig retryConfig,
            String type) {

        this.vertx = vertx;
        this.retryConfig = retryConfig;
        this.options = new RedisOptions().setConnectionString("redis://:" + password + "@" + host + ":" + port);
        this.type = type;
    }

    public void start(Promise<Void> startFuture) {
        createRedisClient(onCreate -> {
            if (onCreate.succeeded()) {
                logger.info("Confiant Redis {} connection is established", type);
                startFuture.tryComplete();
            }
        }, false);
    }

    public RedisAPI getRedisAPI() {
        return redisAPI;
    }

    /**
     * Will create a redis client and setup a reconnect handler when there is
     * an exception in the connection.
     */
    private void createRedisClient(Handler<AsyncResult<RedisConnection>> handler, boolean isReconnect) {
        Redis.createClient(vertx, options)
                .connect(onConnect -> {
                    if (onConnect.succeeded()) {
                        connection = onConnect.result();
                        connection.exceptionHandler(e -> {
                            if (connection != null) {
                                connection.close();
                                connection = null;
                                attemptReconnect(0, handler);
                            }
                        });
                        connection.endHandler(e -> {
                            if (connection != null) {
                                connection.close();
                                connection = null;
                                attemptReconnect(0, handler);
                            }
                        });
                        redisAPI = RedisAPI.api(connection);
                        handler.handle(onConnect);
                    } else if (!isReconnect) {
                        attemptReconnect(0, handler);
                    } else {
                        handler.handle(onConnect);
                    }
                });
    }

    private void attemptReconnect(int retry, Handler<AsyncResult<RedisConnection>> handler) {
        if (retry > (retryConfig.getShortIntervalAttempts() + retryConfig.getLongIntervalAttempts())) {
            logger.info("Confiant Redis connection is not established");
        } else {
            long backoff = retry < retryConfig.getShortIntervalAttempts()
                    ? retryConfig.getShortInterval()
                    : retryConfig.getLongInterval();

            vertx.setTimer(backoff, timer -> createRedisClient(onReconnect -> {
                if (onReconnect.failed()) {
                    attemptReconnect(retry + 1, handler);
                } else if (onReconnect.succeeded()) {
                    handler.handle(onReconnect);
                }
            }, true));
        }
    }
}
