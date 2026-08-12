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
        final RedisAPI readRedisNodeAPI = this.readRedisNode.getRedisAPI();
        if (isScanDisabled || readRedisNodeAPI == null || bids.getBresps().isEmpty()) {
            return Future.succeededFuture(getEmptyScanResult());
        }

        return readRedisNodeAPI.get("function_submit_bids")
                .map(Response::toString)
                .map(response -> List.of(response, "0", toBidsAsJson(bids), apiKey, "true"))
                .compose(args -> scanBids(args, readRedisNodeAPI))
                .otherwise(ignored -> getEmptyScanResult());
    }

    private Future<BidsScanResult> scanBids(List<String> args, RedisAPI redisAPI) {
        return redisAPI.evalsha(args)
                .map(Response::toString)
                .map(redisParser::parseBidsScanResult)
                .compose(parsedResult -> parsedResult.getBidScanResults()
                        .stream().anyMatch(BidScanResult::isRoSkipped)
                        ? reSubmitBidsToWriteNode(args)
                        : Future.succeededFuture(parsedResult));
    }

    private Future<BidsScanResult> reSubmitBidsToWriteNode(List<String> readArgs) {
        final RedisAPI writeRedisAPI = this.writeRedisNode.getRedisAPI();
        if (writeRedisAPI == null) {
            return Future.succeededFuture(getEmptyScanResult());
        }

        final List<String> writeArgs = readArgs.stream().limit(4).toList();
        return writeRedisAPI.evalsha(writeArgs)
                .map(Response::toString)
                .map(redisParser::parseBidsScanResult);
    }

    public Future<Boolean> isScanDisabledFlag() {
        final RedisAPI redisAPI = this.readRedisNode.getRedisAPI();
        if (redisAPI == null) {
            return Future.succeededFuture(true);
        }

        return redisAPI.get("scan-disabled")
                .map(Response::toString)
                .map(Boolean::parseBoolean)
                .otherwise(false);
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
