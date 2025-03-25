package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.Response;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisBidsData;

import java.util.Collections;
import java.util.List;

public class BidsScanner {

    private final RedisParser redisParser;

    private final String apiKey;

    private final RedisClient writeRedisNode;

    private final RedisClient readRedisNode;

    private volatile Boolean isScanDisabled = true;

    private final ObjectMapper objectMapper;

    public BidsScanner(
            RedisClient writeRedisNode,
            RedisClient readRedisNode,
            String apiKey,
            ObjectMapper objectMapper) {

        this.writeRedisNode = writeRedisNode;
        this.readRedisNode = readRedisNode;
        this.apiKey = apiKey;
        this.objectMapper = objectMapper;
        this.redisParser = new RedisParser(objectMapper);
    }

    public void start(Promise<Void> startFuture) {
        final Promise<Void> startWriteRedis = Promise.promise();
        startWriteRedis.future().onComplete(r -> readRedisNode.start(startFuture));

        writeRedisNode.start(startWriteRedis);
    }

    public void enableScan() {
        isScanDisabled = false;
    }

    public void disableScan() {
        isScanDisabled = true;
    }

    public Future<BidsScanResult> submitBids(RedisBidsData bids) {
        final Promise<BidsScanResult> scanResult = Promise.promise();

        final RedisAPI readRedisNodeAPI = this.readRedisNode.getRedisAPI();
        final boolean shouldSubmit = !isScanDisabled
                && readRedisNodeAPI != null && bids.getBresps().size() > 0;

        if (shouldSubmit) {
            readRedisNodeAPI.get("function_submit_bids", submitHash -> {
                final Object submitHashResult = submitHash.result();
                if (submitHashResult != null) {
                    final List<String> readArgs = List.of(
                            submitHashResult.toString(),
                            "0",
                            toBidsAsJson(bids),
                            apiKey,
                            "true");

                    readRedisNodeAPI.evalsha(readArgs, response -> {
                        if (response.result() != null) {
                            final BidsScanResult parserResult = redisParser
                                    .parseBidsScanResult(response.result().toString());
                            final boolean isAnyRoSkipped = parserResult.getBidScanResults()
                                    .stream().anyMatch(BidScanResult::isRoSkipped);

                            if (isAnyRoSkipped) {
                                reSubmitBidsToWriteNode(readArgs, scanResult);
                            } else {
                                scanResult.complete(parserResult);
                            }
                        } else {
                            scanResult.complete(getEmptyScanResult());
                        }
                    });
                } else {
                    scanResult.complete(getEmptyScanResult());
                }
            });

            return scanResult.future();
        }

        return Future.succeededFuture(getEmptyScanResult());
    }

    private void reSubmitBidsToWriteNode(List<String> readArgs, Promise<BidsScanResult> scanResult) {
        final RedisAPI writeRedisAPI = this.writeRedisNode.getRedisAPI();
        if (writeRedisAPI != null) {
            final List<String> writeArgs = readArgs.stream().limit(4).toList();
            writeRedisAPI.evalsha(writeArgs, response -> {
                if (response.result() != null) {
                    final BidsScanResult parserResult = redisParser
                            .parseBidsScanResult(response.result().toString());

                    scanResult.complete(parserResult);
                } else {
                    scanResult.complete(getEmptyScanResult());
                }
            });
        } else {
            scanResult.complete(getEmptyScanResult());
        }
    }

    public Future<Boolean> isScanDisabledFlag() {
        final RedisAPI redisAPI = this.readRedisNode.getRedisAPI();
        final Promise<Boolean> isDisabled = Promise.promise();

        if (redisAPI != null) {
            redisAPI.get("scan-disabled", scanDisabledValue -> {
                final Response scanDisabled = scanDisabledValue.result();
                isDisabled.complete(scanDisabled != null && "true".equals(scanDisabled.toString()));
            });

            return isDisabled.future();
        }

        return Future.succeededFuture(true);
    }

    private String toBidsAsJson(RedisBidsData bids) {
        try {
            return objectMapper.writeValueAsString(bids);
        } catch (JsonProcessingException ignored) {
            return "";
        }
    }

    private BidsScanResult getEmptyScanResult() {
        return BidsScanResult.builder()
                .bidScanResults(Collections.emptyList())
                .debugMessages(Collections.emptyList())
                .build();
    }
}
