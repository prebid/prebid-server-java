package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.GroupByIssues;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.hooks.modules.com.confiant.adquality.util.AdQualityModuleTestUtils.getBidderResponse;

public class BidsScanResultTest {

    private final RedisParser redisParser = new RedisParser(new ObjectMapper());

    @Test
    public void shouldProperlyGetIssuesMessage() {
        // given
        final String redisResponse = """
                [[[{
                    "tag_key": "key_a",
                    "imp_id": "imp_a",
                    "issues": [{
                        "value": "ads.deceivenetworks.net",
                        "spec_name": "malicious_domain",
                        "first_adinstance": "e91e8da982bb8b7f80100426"
                    }]
                }]]]""";
        final BidsScanResult bidsScanResult = redisParser.parseBidsScanResult(redisResponse);

        // when
        final List<String> issues = bidsScanResult.getIssuesMessages();

        // then
        assertThat(issues.size()).isEqualTo(1);
        assertThat(issues.getFirst()).isEqualTo("""
                key_a: [\
                Issue(specName=malicious_domain, \
                value=ads.deceivenetworks.net, \
                firstAdinstance=e91e8da982bb8b7f80100426)]""");
    }

    @Test
    public void shouldProperlyGetDebugMessage() {
        // given
        final String redisResponse = "invalid redis response";
        final BidsScanResult bidsScanResult = redisParser.parseBidsScanResult(redisResponse);

        // when
        final List<String> messages = bidsScanResult.getDebugMessages();

        // then
        assertThat(messages.size()).isEqualTo(1);
        assertThat(messages.getFirst()).isEqualTo("Error during parse redis response: invalid redis response");
    }

    @Test
    public void shouldProperlyGroupBiddersByIssues() {
        // given
        final String redisResponse = """
                [[
                    [{
                        "tag_key": "key_a",
                        "imp_id": "imp_a",
                        "issues": [{
                            "value": "ads.deceivenetworks.net",
                            "spec_name": "malicious_domain",
                            "first_adinstance": "e91e8da982bb8b7f80100426"
                        }]
                    }],
                    [{
                        "tag_key": "key_b",
                        "imp_id": "imp_b"
                    }]
                ]]""";
        final BidsScanResult bidsScanResult = redisParser.parseBidsScanResult(redisResponse);
        final BidderResponse br1 = getBidderResponse("critio1", "1", "11");
        final BidderResponse br2 = getBidderResponse("critio2", "2", "12");

        // when
        final GroupByIssues<BidderResponse> groupByIssues = bidsScanResult.toGroupByIssues(List.of(br1, br2));

        // then
        assertThat(groupByIssues.getWithIssues().size()).isEqualTo(1);
        assertThat(groupByIssues.getWithIssues().get(0).getBidder()).isEqualTo("critio1");
        assertThat(groupByIssues.getWithoutIssues().size()).isEqualTo(1);
        assertThat(groupByIssues.getWithoutIssues().get(0).getBidder()).isEqualTo("critio2");
    }

    @Test
    public void shouldProperlyGroupBiddersByIssuesWithoutIssues() {
        // given
        final String redisResponse = """
                [[
                    [{
                        "tag_key": "key_a",
                        "imp_id": "imp_a"
                    }],
                    [{
                        "tag_key": "key_b",
                        "imp_id": "imp_b"
                    }]
                ]]""";
        final BidsScanResult bidsScanResult = redisParser.parseBidsScanResult(redisResponse);
        final BidderResponse br1 = getBidderResponse("critio1", "1", "11");
        final BidderResponse br2 = getBidderResponse("critio2", "2", "12");

        // when
        final GroupByIssues<BidderResponse> groupByIssues = bidsScanResult.toGroupByIssues(List.of(br1, br2));

        // then
        assertThat(groupByIssues.getWithIssues().size()).isEqualTo(0);
        assertThat(groupByIssues.getWithoutIssues().size()).isEqualTo(2);
        assertThat(groupByIssues.getWithoutIssues().get(0).getBidder()).isEqualTo("critio1");
        assertThat(groupByIssues.getWithoutIssues().get(1).getBidder()).isEqualTo("critio2");
    }
}
