package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RedisParserTest {

    private final RedisParser redisParser = new RedisParser(new ObjectMapper());

    @Test
    public void shouldParseBidsScanResult() {
        // given
        final String redisResponse = """
                [
                    [[{
                        "tag_key": "key_a",
                        "imp_id": "imp_a"
                    }]],
                    [[{
                        "tag_key": "key_b",
                        "imp_id": "imp_b"
                    }]]
                ]""";

        // when
        final BidsScanResult actualScanResults = redisParser.parseBidsScanResult(redisResponse);

        // then
        assertThat(actualScanResults.getBidScanResults().get(0).getTagKey()).isEqualTo("key_a");
        assertThat(actualScanResults.getBidScanResults().get(0).getImpId()).isEqualTo("imp_a");
        assertThat(actualScanResults.getBidScanResults().get(1).getTagKey()).isEqualTo("key_b");
        assertThat(actualScanResults.getBidScanResults().get(1).getImpId()).isEqualTo("imp_b");
        assertThat(actualScanResults.getDebugMessages().size()).isEqualTo(0);
    }

    @Test
    public void shouldParseFullBidsScanResult() {
        // given
        final String redisResponse = """
                [[[{
                  "tag_key": "tg",
                  "imp_id": "123",
                  "known_creative": true,
                  "ro_skipped": false,
                  "issues": [{
                    "value": "ads.deceivenetworks.net",
                    "spec_name": "malicious_domain",
                    "first_adinstance": "e91e8da982bb8b7f80100426"
                  }],
                  "attributes": {
                    "is_ssl": true,
                    "ssl_error": false,
                    "width": 600,
                    "height": 300,
                    "anim": 5,
                    "network_load_startup": 1024,
                    "network_load_polite": 1024,
                    "vast": {
                      "redirects": 3
                    },
                    "brands": [
                      "Pfizer"
                    ],
                    "categories": [
                      {
                        "code": "CAT-2",
                        "name": "Health and Medical Services"
                      },
                      {
                        "code": "CAT-75",
                        "name": "Pharmaceutical Drugs"
                      }
                    ]
                  },
                  "metrics": {
                    "submitted": "2017-05-10T13:29:28-04:00",
                    "fetched":"2017-05-10T13:29:29-04:00",
                    "scanned":"2017-07-22T11:49:40-04:00",
                    "synchronized": {
                      "first":"2017-05-10T13:29:55-04:00",
                      "last":"2017-07-24T00:52:04-04:00"
                    }
                  },
                  "adinstance": "qwerty"
                }]]]""";

        // when
        final BidsScanResult actualScanResults = redisParser.parseBidsScanResult(redisResponse);

        // then
        assertThat(actualScanResults.getBidScanResults().getFirst().getTagKey()).isEqualTo("tg");
        assertThat(actualScanResults.getBidScanResults().size()).isEqualTo(1);
        assertThat(actualScanResults.getDebugMessages().size()).isEqualTo(0);
    }

    @Test
    public void shouldParseBidsScanResultWithError() {
        // given
        final String redisResponse = """
                {"code": "123", "message": "error message", "error": true, "dsp_id": "cri"}""";

        // when
        final BidsScanResult actualScanResults = redisParser.parseBidsScanResult(redisResponse);

        // then
        assertThat(actualScanResults.getBidScanResults().size()).isEqualTo(0);
        assertThat(actualScanResults.getDebugMessages().getFirst()).isEqualTo("Redis error - 123: error message");
    }

    @Test
    public void shouldParseBidsScanResultWithInvalidResponse() {
        // given
        final String redisResponse = "invalid redis response";

        // when
        final BidsScanResult actualScanResults = redisParser.parseBidsScanResult(redisResponse);

        // then
        assertThat(actualScanResults.getBidScanResults().size()).isEqualTo(0);
        assertThat(actualScanResults.getDebugMessages().getFirst())
                .isEqualTo("Error during parse redis response: invalid redis response");
    }
}
