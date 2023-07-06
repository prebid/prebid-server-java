package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.Response;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.OperationResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisBidsData;

import java.util.Collections;
import java.util.List;

public class RedisClient {

    private final RedisParser redisParser = new RedisParser();

    private final String apiKey;

    private final RedisVerticle redisVerticle;

    public RedisClient(RedisVerticle redisVerticle, String apiKey) {
        this.apiKey = apiKey;
        this.redisVerticle = redisVerticle;
    }

    public void start(Promise<Void> startFuture) {
        redisVerticle.start(startFuture);
    }

    public Future<BidsScanResult> submitBids(RedisBidsData bids) {
        final Promise<BidsScanResult> scanResult = Promise.promise();
        final RedisAPI redisAPI = this.redisVerticle.getRedisAPI();
        if (redisAPI != null && bids.getBresps().size() > 0) {
            redisAPI.get("function_submit_bids", submitHash -> redisAPI
                    .evalsha(List.of(submitHash.result().toString(), "0", bids.toJson(), apiKey), response -> {
                        final OperationResult<List<BidScanResult>> parserResult = redisParser
                                .parseBidsScanResult(response.result().toString());

                        scanResult.complete(new BidsScanResult(parserResult));
                    }));

            return scanResult.future();
        }

        return Future.succeededFuture(new BidsScanResult(OperationResult.<List<BidScanResult>>builder()
                .value(Collections.emptyList())
                .debugMessages(Collections.emptyList())
                .build()));
    }

    public Future<Boolean> isScanDisabled() {
        final RedisAPI redisAPI = this.redisVerticle.getRedisAPI();
        final Promise<Boolean> isDisabled = Promise.promise();

        if (redisAPI != null) {
            redisAPI.get("scan-disabled", scanDisabledValue -> {
                final Response scanDisabled = scanDisabledValue.result();
                isDisabled.complete(scanDisabled != null && scanDisabled.toString().equals("true"));
            });

            return isDisabled.future();
        }

        return Future.succeededFuture(true);
    }
}
