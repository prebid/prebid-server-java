package org.prebid.server.hooks.modules.com.confiant.adquality.v1;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisClient;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisParser;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidsScanResult;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesPayload;
import org.prebid.server.hooks.v1.bidder.BidResponsesInvocationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

public class ConfiantAdQualityBidResponsesScanHookTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RedisClient redisClient;

    @Mock
    private AllProcessedBidResponsesPayload allProcessedBidResponsesPayload;

    @Mock
    private BidResponsesInvocationContext bidResponsesInvocationContext;

    private ConfiantAdQualityBidResponsesScanHook hook;

    private final RedisParser redisParser = new RedisParser();

    @Before
    public void setUp() {
        hook = new ConfiantAdQualityBidResponsesScanHook(redisClient);
    }

    @Test
    public void shouldHaveValidInitialConfigs() {
        // given

        // when

        // then
        assertThat(hook.code()).isEqualTo("confiant-ad-quality-bid-responses-scan-hook");
    }

    @Test
    public void shouldReturnResultWithNoActionWhenRedisHasNoAnswer() {
        // given
        BidsScanResult bidsScanResult = new BidsScanResult();
        doReturn(bidsScanResult).when(redisClient).submitBids(any());

        // when
        Future<InvocationResult<AllProcessedBidResponsesPayload>> future = hook.call(
                allProcessedBidResponsesPayload, bidResponsesInvocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        InvocationResult<AllProcessedBidResponsesPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
    }

    @Test
    public void shouldReturnResultWithUpdateActionWhenRedisHasFoundSomeIssues() {
        // given
        BidsScanResult bidsScanResult = new BidsScanResult();
        bidsScanResult.setScanResults(redisParser.parseBidsScanResult(
                "[[[{\"issues\":[{\"spec_name\":\"malicious_domain\",\"value\":\"ads.deceivenetworks.net\",\"first_adinstance\":\"e91e8da982bb8b7f80100426\"}]}]]]"
        ));

        doReturn(bidsScanResult).when(redisClient).submitBids(any());

        // when
        Future<InvocationResult<AllProcessedBidResponsesPayload>> future = hook.call(
                allProcessedBidResponsesPayload, bidResponsesInvocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        InvocationResult<AllProcessedBidResponsesPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
    }
}
