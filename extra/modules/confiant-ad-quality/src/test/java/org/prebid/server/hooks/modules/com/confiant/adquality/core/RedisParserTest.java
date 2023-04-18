package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import org.junit.Test;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.OperationResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RedisParserTest {

    private final RedisParser redisParser = new RedisParser();

    @Test
    public void shouldParseBidsScanResult() {
        // given
        String redisResponse = "[[[{\"tag_key\": \"key_a\", \"imp_id\": \"imp_a\"}]],[[{\"tag_key\": \"key_b\", \"imp_id\": \"imp_b\"}]]]";

        // when
        OperationResult<List<BidScanResult>> actualScanResults = redisParser.parseBidsScanResult(redisResponse);

        // then
        assertThat(actualScanResults.getValue().get(0).getTagKey()).isEqualTo("key_a");
        assertThat(actualScanResults.getValue().get(0).getImpId()).isEqualTo("imp_a");
        assertThat(actualScanResults.getValue().get(1).getTagKey()).isEqualTo("key_b");
        assertThat(actualScanResults.getValue().get(1).getImpId()).isEqualTo("imp_b");
        assertThat(actualScanResults.getDebugMessages().size()).isEqualTo(0);
    }

    @Test
    public void shouldParseFullBidsScanResult() {
        // given
        String redisResponse = "[[[{\n" +
                "  \"tag_key\": \"tg\",\n" +
                "  \"imp_id\": \"123\",\n" +
                "  \"known_creative\": true,\n" +
                "  \"ro_skipped\": false,\n" +
                "  \"issues\": [{\n" +
                "    \"value\": \"ads.deceivenetworks.net\",\n" +
                "    \"spec_name\": \"malicious_domain\",\n" +
                "    \"first_adinstance\": \"e91e8da982bb8b7f80100426\"\n" +
                "  }],\n" +
                "  \"attributes\": {\n" +
                "    \"is_ssl\": true,\n" +
                "    \"ssl_error\": false,\n" +
                "    \"width\": 600,\n" +
                "    \"height\": 300,\n" +
                "    \"anim\": 5,\n" +
                "    \"network_load_startup\": 1024,\n" +
                "    \"network_load_polite\": 1024,\n" +
                "    \"vast\": {\n" +
                "      \"redirects\": 3\n" +
                "    },\n" +
                "    \"brands\": [\n" +
                "      \"Pfizer\"\n" +
                "    ],\n" +
                "    \"categories\": [\n" +
                "      {\n" +
                "        \"code\": \"CAT-2\",\n" +
                "        \"name\": \"Health and Medical Services\"\n" +
                "      },\n" +
                "      {\n" +
                "        \"code\": \"CAT-75\",\n" +
                "        \"name\": \"Pharmaceutical Drugs\"\n" +
                "      }\n" +
                "    ]\n" +
                "  },\n" +
                "  \"metrics\": {\n" +
                "    \"submitted\": \"2017-05-10T13:29:28-04:00\",\n" +
                "    \"fetched\":\"2017-05-10T13:29:29-04:00\",\n" +
                "    \"scanned\":\"2017-07-22T11:49:40-04:00\",\n" +
                "    \"synchronized\": {\n" +
                "      \"first\":\"2017-05-10T13:29:55-04:00\",\n" +
                "      \"last\":\"2017-07-24T00:52:04-04:00\"\n" +
                "    }\n" +
                "  },\n" +
                "  \"adinstance\": \"qwerty\"\n" +
                "}]]]";

        // when
        OperationResult<List<BidScanResult>> actualScanResults = redisParser.parseBidsScanResult(redisResponse);

        // then
        assertThat(actualScanResults.getValue().get(0).getTagKey()).isEqualTo("tg");
        assertThat(actualScanResults.getValue().size()).isEqualTo(1);
        assertThat(actualScanResults.getDebugMessages().size()).isEqualTo(0);
    }

    @Test
    public void shouldParseBidsScanResultWithError() {
        // given
        String redisResponse = "{\"code\": \"123\", \"message\": \"error message\", \"error\": true, \"dsp_id\": \"cri\"}";

        // when
        OperationResult<List<BidScanResult>> actualScanResults = redisParser.parseBidsScanResult(redisResponse);

        // then
        assertThat(actualScanResults.getValue().size()).isEqualTo(0);
        assertThat(actualScanResults.getDebugMessages().get(0)).isEqualTo("Redis error - 123: error message");
    }

    @Test
    public void shouldParseBidsScanResultWithInvalidResponse() {
        // given
        String redisResponse = "invalid redis response";

        // when
        OperationResult<List<BidScanResult>> actualScanResults = redisParser.parseBidsScanResult(redisResponse);

        // then
        assertThat(actualScanResults.getValue().size()).isEqualTo(0);
        assertThat(actualScanResults.getDebugMessages().get(0)).isEqualTo("Error during parse redis response: invalid redis response");
    }
}
