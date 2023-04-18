package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidsScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisBidsData;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisException;

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
            logger.info("Can't establish Redis connection {0}", e);
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
        final BidsScanResult bidsScanResults = new BidsScanResult();
        if (jedis != null) {
            try {
                final String submitHash = jedis.get("function_submit_bids");
                final String response = jedis.evalsha(submitHash, 0, bids.toJson(), apiKey).toString();
                final BidScanResult[][][] scanResults = redisParser.parseBidsScanResult(response);

                if (scanResults != null) {
                    bidsScanResults.setScanResults(redisParser.parseBidsScanResult(response));
                }
            } catch (JedisException e) {
                logger.info(e);
            }
        }

        return bidsScanResults;
    }
}
