package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import org.junit.Test;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;

import static org.assertj.core.api.Assertions.assertThat;

public class RedisParserTest {

    private final RedisParser redisParser = new RedisParser();

    @Test
    public void shouldParseBidsScanResult() {
        // given
        String redisResponse = "[[[{\"tag_key\": \"key_a\", \"imp_id\": \"imp_a\"}]],[[{\"tag_key\": \"key_b\", \"imp_id\": \"imp_b\"}]]]";

        // when
        BidScanResult[][][] actualScanResults = redisParser.parseBidsScanResult(redisResponse);

        // then
        assertThat(actualScanResults[0][0][0].getTagKey()).isEqualTo("key_a");
        assertThat(actualScanResults[0][0][0].getImpId()).isEqualTo("imp_a");

        assertThat(actualScanResults[1][0][0].getTagKey()).isEqualTo("key_b");
        assertThat(actualScanResults[1][0][0].getImpId()).isEqualTo("imp_b");
    }

    @Test
    public void shouldParseBidsScanResultWithError() {
        // given
        String redisResponse = "{\"code\": \"123\", \"message\": \"error message\", \"error\": true, \"dsp_id\": \"cri\"}";

        // when
        BidScanResult[][][] actualScanResults = redisParser.parseBidsScanResult(redisResponse);

        // then
        assertThat(actualScanResults).isNull();
    }

    @Test
    public void shouldParseBidsScanResultWithInvalidResponse() {
        // given
        String redisResponse = "invalid redis response";

        // when
        BidScanResult[][][] actualScanResults = redisParser.parseBidsScanResult(redisResponse);

        // then
        assertThat(actualScanResults).isNull();
    }
}
