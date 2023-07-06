package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.redis.client.Redis;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.RedisConnection;
import io.vertx.redis.client.RedisOptions;

public class RedisVerticle extends AbstractVerticle {

    private static final Logger logger = LoggerFactory.getLogger(RedisVerticle.class);

    private static final int MAX_RECONNECT_RETRIES = 16;

    private final RedisOptions options;

    private RedisConnection client;

    private RedisAPI redisAPI;

    private final Vertx vertx;

    public RedisVerticle(Vertx vertx, String host, int port, String password) {
        this.vertx = vertx;
        this.options = new RedisOptions().setConnectionString("redis://:" + password + "@" + host + ":" + port);
    }

    @Override
    public void start(Promise<Void> startFuture) {
        createRedisClient(onCreate -> {
            if (onCreate.succeeded()) {
                logger.info("Confiant Redis connection is established");
                startFuture.complete();
            }
        });
    }

    public RedisAPI getRedisAPI() {
        return redisAPI;
    }

    /**
     * Will create a redis client and setup a reconnect handler when there is
     * an exception in the connection.
     */
    private void createRedisClient(Handler<AsyncResult<RedisConnection>> handler) {
        Redis.createClient(vertx, options)
                .connect(onConnect -> {
                    if (onConnect.succeeded()) {
                        client = onConnect.result();
                        client.exceptionHandler(e -> attemptReconnect(0));
                        redisAPI = RedisAPI.api(client);
                    }
                    handler.handle(onConnect);
                });
    }

    /**
     * Attempt to reconnect up to MAX_RECONNECT_RETRIES
     */
    private void attemptReconnect(int retry) {
        if (retry > MAX_RECONNECT_RETRIES) {
            logger.info("Confiant Redis connection is not established");
        } else {
            // retry with backoff up to 10240 ms
            long backoff = (long) (Math.pow(2, Math.min(retry, 10)) * 10);

            vertx.setTimer(backoff, timer -> createRedisClient(onReconnect -> {
                if (onReconnect.failed()) {
                    attemptReconnect(retry + 1);
                }
            }));
        }
    }
}
