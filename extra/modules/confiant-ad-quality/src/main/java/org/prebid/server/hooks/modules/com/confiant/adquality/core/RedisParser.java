package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisError;

public class RedisParser {

    private static final Logger logger = LoggerFactory.getLogger(RedisParser.class);

    public BidScanResult[][][] parseBidsScanResult(String redisResponse) {
        BidScanResult[][][] scanResults = null;

        final ObjectMapper objectMapper = new ObjectMapper();

        try {
            scanResults = objectMapper.readValue(redisResponse, BidScanResult[][][].class);
        } catch (JsonProcessingException resultParse) {
            try {
                RedisError errorResponse = objectMapper.readValue(redisResponse, RedisError.class);
                logger.info("Redis error - {0}: {1}", errorResponse.getCode(), errorResponse.getMessage());
            } catch (JsonProcessingException errorParse) {
                logger.info("Error during parse redis response: {0}", redisResponse);
            }
        }

        return scanResults;
    }
}
