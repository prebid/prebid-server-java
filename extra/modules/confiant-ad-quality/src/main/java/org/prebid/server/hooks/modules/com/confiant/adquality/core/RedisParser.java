package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisError;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RedisParser {

    private static final Logger logger = LoggerFactory.getLogger(RedisParser.class);

    private final ObjectMapper objectMapper;

    public RedisParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public BidsScanResult parseBidsScanResult(String redisResponse) {
        try {
            final BidScanResult[][][] draftResponse = objectMapper.readValue(redisResponse, BidScanResult[][][].class);
            final List<BidScanResult> scanResultsFlat = Arrays.stream(draftResponse)
                    .flatMap(Arrays::stream)
                    .map(array -> array[0])
                    .toList();

            return BidsScanResult.builder()
                    .bidScanResults(scanResultsFlat)
                    .debugMessages(Collections.emptyList())
                    .build();
        } catch (JsonProcessingException resultParse) {
            String message;
            try {
                RedisError errorResponse = objectMapper.readValue(redisResponse, RedisError.class);
                message = String.format("Redis error - %s: %s", errorResponse.getCode(), errorResponse.getMessage());
            } catch (JsonProcessingException errorParse) {
                message = String.format("Error during parse redis response: %s", redisResponse);
            }
            logger.warn(message);
            return BidsScanResult.builder()
                    .bidScanResults(Collections.emptyList())
                    .debugMessages(List.of(message))
                    .build();
        }
    }
}
