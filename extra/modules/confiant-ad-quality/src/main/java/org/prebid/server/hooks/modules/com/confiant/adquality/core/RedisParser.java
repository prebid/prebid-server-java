package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.OperationResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisError;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class RedisParser {

    private static final Logger logger = LoggerFactory.getLogger(RedisParser.class);

    public OperationResult<List<BidScanResult>> parseBidsScanResult(String redisResponse) {
        final ObjectMapper objectMapper = new ObjectMapper();

        try {
            BidScanResult[][][] draftResponse = objectMapper.readValue(redisResponse, BidScanResult[][][].class);
            List<BidScanResult> scanResultsFlat = Arrays.stream(draftResponse)
                    .flatMap(Arrays::stream)
                    .map(array -> array[0])
                    .toList();

            return OperationResult.<List<BidScanResult>>builder()
                    .value(scanResultsFlat)
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
            logger.info(message);
            return OperationResult.<List<BidScanResult>>builder()
                    .value(Collections.emptyList())
                    .debugMessages(List.of(message))
                    .build();
        }
    }
}
