package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import org.junit.Test;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.OperationResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.util.AdQualityModuleTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class BidsScanResultTest {

    private final RedisParser redisParser = new RedisParser();

    @Test
    public void shouldNotHaveIssuesInTheResult() {
        // given
        final String redisResponse = "[[[{\"tag_key\": \"key_a\", \"imp_id\": \"imp_a\"}]]]";
        final OperationResult<List<BidScanResult>> results = redisParser.parseBidsScanResult(redisResponse);
        final BidsScanResult bidsScanResult = new BidsScanResult(results);

        // when
        final boolean hasIssues = bidsScanResult.hasIssues();

        // then
        assertThat(hasIssues).isFalse();
    }

    @Test
    public void shouldHaveIssuesInTheResult() {
        // given
        final String redisResponse = "[[[{\"tag_key\": \"key_a\", \"imp_id\": \"imp_a\", \"issues\": [{ \"value\": \"ads.deceivenetworks.net\", \"spec_name\": \"malicious_domain\", \"first_adinstance\": \"e91e8da982bb8b7f80100426\"}]}]]]";
        final OperationResult<List<BidScanResult>> results = redisParser.parseBidsScanResult(redisResponse);
        final BidsScanResult bidsScanResult = new BidsScanResult(results);

        // when
        final boolean hasIssues = bidsScanResult.hasIssues();

        // then
        assertThat(hasIssues).isTrue();
    }

    @Test
    public void shouldProperlyGetIssuesMessage() {
        // given
        final String redisResponse = "[[[{\"tag_key\": \"key_a\", \"imp_id\": \"imp_a\", \"issues\": [{ \"value\": \"ads.deceivenetworks.net\", \"spec_name\": \"malicious_domain\", \"first_adinstance\": \"e91e8da982bb8b7f80100426\"}]}]]]";
        final OperationResult<List<BidScanResult>> results = redisParser.parseBidsScanResult(redisResponse);
        final BidsScanResult bidsScanResult = new BidsScanResult(results);

        // when
        final List<String> issues = bidsScanResult.getIssuesMessages();

        // then
        assertThat(issues.size()).isEqualTo(1);
        assertThat(issues.get(0)).isEqualTo("key_a: [Issue(specName=malicious_domain, value=ads.deceivenetworks.net, firstAdinstance=e91e8da982bb8b7f80100426)]");
    }

    @Test
    public void shouldProperlyGetDebugMessage() {
        // given
        final String redisResponse = "invalid redis response";
        final OperationResult<List<BidScanResult>> results = redisParser.parseBidsScanResult(redisResponse);
        final BidsScanResult bidsScanResult = new BidsScanResult(results);

        // when
        final List<String> messages = bidsScanResult.getDebugMessages();

        // then
        assertThat(messages.size()).isEqualTo(1);
        assertThat(messages.get(0)).isEqualTo("Error during parse redis response: invalid redis response");
    }

    @Test
    public void shouldProperlyBuildBiddersMapWithIssues() {
        // given
        final String redisResponse = "[[[{\"tag_key\": \"key_a\", \"imp_id\": \"imp_a\", \"issues\": [{ \"value\": \"ads.deceivenetworks.net\", \"spec_name\": \"malicious_domain\", \"first_adinstance\": \"e91e8da982bb8b7f80100426\"}]}],[{\"tag_key\": \"key_b\", \"imp_id\": \"imp_b\"}]]]";
        final OperationResult<List<BidScanResult>> results = redisParser.parseBidsScanResult(redisResponse);
        final BidsScanResult bidsScanResult = new BidsScanResult(results);
        final BidderResponse br1 = AdQualityModuleTestUtils.getBidderResponse("critio1", "1", "11");
        final BidderResponse br2 = AdQualityModuleTestUtils.getBidderResponse("critio2", "2", "12");

        // when
        Map<Boolean, List<BidderResponse>> issuesExistencyMap = bidsScanResult.toIssuesExistencyMap(List.of(br1, br2));

        // then
        assertThat(issuesExistencyMap.get(true).size()).isEqualTo(1);
        assertThat(issuesExistencyMap.get(true).get(0).getBidder()).isEqualTo("critio1");
        assertThat(issuesExistencyMap.get(false).size()).isEqualTo(1);
        assertThat(issuesExistencyMap.get(false).get(0).getBidder()).isEqualTo("critio2");
    }

    @Test
    public void shouldProperlyBuildBiddersMapWithoutIssues() {
        // given
        final String redisResponse = "[[[{\"tag_key\": \"key_a\", \"imp_id\": \"imp_a\"}],[{\"tag_key\": \"key_b\", \"imp_id\": \"imp_b\"}]]]";
        final OperationResult<List<BidScanResult>> results = redisParser.parseBidsScanResult(redisResponse);
        final BidsScanResult bidsScanResult = new BidsScanResult(results);
        final BidderResponse br1 = AdQualityModuleTestUtils.getBidderResponse("critio1", "1", "11");
        final BidderResponse br2 = AdQualityModuleTestUtils.getBidderResponse("critio2", "2", "12");

        // when
        Map<Boolean, List<BidderResponse>> issuesExistencyMap = bidsScanResult.toIssuesExistencyMap(List.of(br1, br2));

        // then
        assertThat(issuesExistencyMap.get(true).size()).isEqualTo(0);
        assertThat(issuesExistencyMap.get(false).size()).isEqualTo(2);
        assertThat(issuesExistencyMap.get(false).get(0).getBidder()).isEqualTo("critio1");
        assertThat(issuesExistencyMap.get(false).get(1).getBidder()).isEqualTo("critio2");
    }
}
