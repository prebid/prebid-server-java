package org.prebid.server.hooks.modules.com.confiant.adquality.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.response.BidResponse;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.redis.client.Command;
import io.vertx.redis.client.RedisAPI;
import io.vertx.redis.client.Response;
import io.vertx.redis.client.ResponseType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.GroupByIssues;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisBidResponseData;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.RedisBidsData;
import org.prebid.server.hooks.modules.com.confiant.adquality.util.AdQualityModuleTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class BidsScannerTest {

    @Mock
    private RedisClient writeRedisNode;

    @Mock
    private RedisClient readRedisNode;

    @Mock
    private RedisAPI redisAPI;

    private BidsScanner bidsScannerTest;

    @BeforeEach
    public void setUp() {
        bidsScannerTest = new BidsScanner(writeRedisNode, readRedisNode, "api-key", new ObjectMapper());
    }

    @Test()
    public void shouldStartRedisVerticle() {
        // given
        final Promise<Void> startFuture = Promise.promise();

        // when
        bidsScannerTest.start(startFuture);

        // then
        verify(writeRedisNode).start((Promise<Void>) any());
    }

    @Test()
    public void shouldReturnScanIsDisabledWhenApiIsNotInitialized() {
        // given
        doReturn(null).when(readRedisNode).getRedisAPI();

        // when
        final Future<Boolean> isScanDisabled = bidsScannerTest.isScanDisabledFlag();

        // then
        assertThat(isScanDisabled.succeeded()).isTrue();
        assertThat(isScanDisabled.result()).isTrue();
    }

    @Test()
    public void shouldReturnScanIsDisabledWhenRedisFlagIsTrue() {
        // given
        final RedisAPI redisAPI = getRedisEmulationWithAnswer("true");
        doReturn(redisAPI).when(readRedisNode).getRedisAPI();

        // when
        final Future<Boolean> isScanDisabled = bidsScannerTest.isScanDisabledFlag();

        // then
        assertThat(isScanDisabled.succeeded()).isTrue();
        assertThat(isScanDisabled.result()).isTrue();
    }

    @Test()
    public void shouldReturnScanIsNotDisabledWhenRedisFlagIsFalse() {
        // given
        final RedisAPI redisAPI = getRedisEmulationWithAnswer("false");
        doReturn(redisAPI).when(readRedisNode).getRedisAPI();

        // when
        final Future<Boolean> isScanDisabled = bidsScannerTest.isScanDisabledFlag();

        // then
        assertThat(isScanDisabled.succeeded()).isTrue();
        assertThat(isScanDisabled.result()).isFalse();
    }

    @Test()
    public void shouldReturnEmptyScanResultWhenApiIsNotInitialized() {
        // given
        doReturn(null).when(readRedisNode).getRedisAPI();

        // when
        final Future<BidsScanResult> scanResult = bidsScannerTest.submitBids(RedisBidsData.builder().build());
        final GroupByIssues<BidderResponse> groupByIssues = scanResult.result().toGroupByIssues(List.of());

        // then
        assertThat(scanResult.succeeded()).isTrue();
        assertThat(groupByIssues.getWithIssues().size()).isEqualTo(0);
        assertThat(groupByIssues.getWithoutIssues().size()).isEqualTo(0);
    }

    @Test()
    public void shouldReturnEmptyScanResultWhenThereIsNoBidderResponses() {
        // given
        doReturn(redisAPI).when(readRedisNode).getRedisAPI();

        // when
        final Future<BidsScanResult> scanResult = bidsScannerTest.submitBids(RedisBidsData.builder().bresps(List.of()).build());
        final GroupByIssues<BidderResponse> groupByIssues = scanResult.result().toGroupByIssues(List.of());

        // then
        assertThat(scanResult.succeeded()).isTrue();
        assertThat(groupByIssues.getWithIssues().size()).isEqualTo(0);
        assertThat(groupByIssues.getWithoutIssues().size()).isEqualTo(0);
    }

    @Test()
    public void shouldReturnEmptyScanResultWhenThereIsSomeBidderResponseAndScanIsDisabled() {
        // given
        final String redisResponse = "[[[{\"tag_key\": \"key_a\", \"imp_id\": \"imp_a\", \"issues\": [{ \"value\": \"ads.deceivenetworks.net\", \"spec_name\": \"malicious_domain\", \"first_adinstance\": \"e91e8da982bb8b7f80100426\"}]}]]]";
        final RedisAPI redisAPI = getRedisEmulationWithAnswer(redisResponse);
        final RedisBidsData bidsData = RedisBidsData.builder()
                .breq(BidRequest.builder().build())
                .bresps(List.of(RedisBidResponseData.builder()
                        .dspId("dsp_id")
                        .bidresponse(BidResponse.builder().build())
                        .build())).build();
        doReturn(redisAPI).when(readRedisNode).getRedisAPI();

        // when
        final Future<BidsScanResult> scanResult = bidsScannerTest.submitBids(bidsData);
        final GroupByIssues<BidderResponse> groupByIssues = scanResult.result()
                .toGroupByIssues(List.of(AdQualityModuleTestUtils.getBidderResponse("bidder-a", "imp-a", "imp-id-a")));

        // then
        assertThat(scanResult.succeeded()).isTrue();
        assertThat(groupByIssues.getWithIssues().size()).isEqualTo(0);
        assertThat(groupByIssues.getWithoutIssues().size()).isEqualTo(1);
    }

    @Test()
    public void shouldReturnRedisScanResultFromReadNodeWhenThereAreSomeBidderResponsesAndScanIsEnabled() {
        // given
        final String redisResponse = "[[[{\"tag_key\": \"key_a\", \"imp_id\": \"imp_a\", \"issues\": [{ \"value\": \"ads.deceivenetworks.net\", \"spec_name\": \"malicious_domain\", \"first_adinstance\": \"e91e8da982bb8b7f80100426\"}]}],[{\"tag_key\": \"key_b\", \"imp_id\": \"imp_b\"}]]]";
        final RedisAPI redisAPI = getRedisEmulationWithAnswer(redisResponse);
        final RedisBidsData bidsData = RedisBidsData.builder()
                .breq(BidRequest.builder().build())
                .bresps(List.of(RedisBidResponseData.builder()
                        .dspId("dsp_id")
                        .bidresponse(BidResponse.builder().build())
                        .build())).build();
        bidsScannerTest.enableScan();
        doReturn(redisAPI).when(readRedisNode).getRedisAPI();

        // when
        final Future<BidsScanResult> scanResult = bidsScannerTest.submitBids(bidsData);
        final GroupByIssues<BidderResponse> groupByIssues = scanResult.result()
                .toGroupByIssues(List.of(
                        AdQualityModuleTestUtils.getBidderResponse("bidder-a", "imp-a", "imp-id-a"),
                        AdQualityModuleTestUtils.getBidderResponse("bidder-b", "imp-b", "imp-id-b")));

        // then
        assertThat(scanResult.succeeded()).isTrue();
        assertThat(groupByIssues.getWithIssues().size()).isEqualTo(1);
        assertThat(groupByIssues.getWithoutIssues().size()).isEqualTo(1);
    }

    @Test()
    public void shouldReturnRedisScanResultFromWriteNodeWhenReadNodeHasMissingResults() {
        // given
        final String readRedisResponse = "[[[{\"tag_key\": \"key_a\", \"imp_id\": \"imp_a\", \"ro_skipped\": \"true\"}]]]";
        final RedisAPI readRedisAPI = getRedisEmulationWithAnswer(readRedisResponse);
        final RedisBidsData bidsData = RedisBidsData.builder()
                .breq(BidRequest.builder().build())
                .bresps(List.of(RedisBidResponseData.builder()
                        .dspId("dsp_id")
                        .bidresponse(BidResponse.builder().build())
                        .build())).build();
        bidsScannerTest.enableScan();
        doReturn(readRedisAPI).when(readRedisNode).getRedisAPI();

        final String writeRedisResponse = "[[[{\"tag_key\": \"key_a\", \"imp_id\": \"imp_a\", \"issues\": [{ \"value\": \"ads.deceivenetworks.net\", \"spec_name\": \"malicious_domain\", \"first_adinstance\": \"e91e8da982bb8b7f80100426\"}]}]]]";
        final RedisAPI writeRedisAPI = getRedisEmulationWithAnswer(writeRedisResponse);
        doReturn(writeRedisAPI).when(writeRedisNode).getRedisAPI();

        // when
        final Future<BidsScanResult> scanResult = bidsScannerTest.submitBids(bidsData);
        final GroupByIssues<BidderResponse> groupByIssues = scanResult.result()
                .toGroupByIssues(List.of(AdQualityModuleTestUtils.getBidderResponse("bidder-a", "imp-a", "imp-id-a")));

        // then
        assertThat(scanResult.succeeded()).isTrue();
        assertThat(groupByIssues.getWithIssues().size()).isEqualTo(1);
        assertThat(groupByIssues.getWithoutIssues().size()).isEqualTo(0);
    }

    private RedisAPI getRedisEmulationWithAnswer(String sendAnswer) {
        return new RedisAPI() {
            @Override
            public void close() {
            }

            @Override
            public Future<Response> send(Command command, String... strings) {
                return Future.succeededFuture(new Response() {
                    @Override
                    public ResponseType type() {
                        return null;
                    }

                    @Override
                    public String toString() {
                        return sendAnswer;
                    }
                });
            }
        };
    }
}
