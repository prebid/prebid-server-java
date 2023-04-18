package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.OperationResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisBidsData;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

import java.util.Collections;
import java.util.List;

public class RedisClient {

    private static final Logger logger = LoggerFactory.getLogger(RedisClient.class);

    private final RedisParser redisParser = new RedisParser();

    private final String apiKey;

    private Jedis jedis;

    public RedisClient(
            String apiKey,
            String host,
            int port,
            String password
    ) {
        this.apiKey = apiKey;

        try {
            this.jedis = createJedis(host, port, password);
        } catch (JedisException e) {
            logger.info("Can't establish Redis connection {0}", e.getMessage());
        }
    }

    private Jedis createJedis(String host, int port, String password) {
        DefaultJedisClientConfig config = DefaultJedisClientConfig
                .builder()
                .password(password)
                .build();

        return new Jedis(host, port, config);
    }

    public BidsScanResult submitBids(RedisBidsData bids) {
        if (jedis != null && bids.getBresps().size() > 0) {
            try {
                final String submitHash = jedis.get("function_submit_bids");
                final String response = jedis.evalsha(submitHash, 0, bids.toJson(), apiKey).toString();
                final OperationResult<List<BidScanResult>> parserResult = redisParser.parseBidsScanResult(response);

                return new BidsScanResult(parserResult);
            } catch (JedisException e) {
                logger.info(e.getMessage());
                return new BidsScanResult(OperationResult.<List<BidScanResult>>builder()
                        .value(Collections.emptyList())
                        .debugMessages(List.of(e.getMessage()))
                        .build());
            }
        }

        return new BidsScanResult(OperationResult.<List<BidScanResult>>builder()
                .value(Collections.emptyList())
                .debugMessages(Collections.emptyList())
                .build());
    }

    public boolean isScanDisabled() {
        if (jedis != null) {
            try {
                return jedis.get("scan-disabled").equals("true");
            } catch (JedisException e) {
                logger.info(e.getMessage());
                return true;
            }
        }

        return true;
    }
}
