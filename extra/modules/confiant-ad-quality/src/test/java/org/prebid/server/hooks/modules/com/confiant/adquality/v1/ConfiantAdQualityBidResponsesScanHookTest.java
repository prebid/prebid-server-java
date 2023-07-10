package org.prebid.server.hooks.modules.com.confiant.adquality.v1;

import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.BidsScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisClient;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisParser;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisScanStateChecker;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.OperationResult;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesPayload;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ConfiantAdQualityBidResponsesScanHookTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private RedisClient redisClient;

    @Mock
    private RedisScanStateChecker redisScanStateChecker;

    @Mock
    private AllProcessedBidResponsesPayload allProcessedBidResponsesPayload;

    @Mock
    private AuctionInvocationContext auctionInvocationContext;

    private ConfiantAdQualityBidResponsesScanHook hook;

    private final RedisParser redisParser = new RedisParser();

    @Before
    public void setUp() {
        hook = new ConfiantAdQualityBidResponsesScanHook(redisClient, redisScanStateChecker);
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
        final BidsScanResult bidsScanResult = new BidsScanResult(OperationResult.<List<BidScanResult>>builder()
                .value(Collections.emptyList())
                .debugMessages(Collections.emptyList())
                .build());

        doReturn(Future.succeededFuture(bidsScanResult)).when(redisClient).submitBids(any());
        doReturn(AuctionContext.builder().build()).when(auctionInvocationContext).auctionContext();

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> future = hook.call(
                allProcessedBidResponsesPayload, auctionInvocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AllProcessedBidResponsesPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.errors()).isNull();
        assertThat(result.debugMessages()).isNull();
    }

    @Test
    public void shouldReturnResultWithUpdateActionWhenRedisHasFoundSomeIssues() {
        // given
        final BidsScanResult bidsScanResult = new BidsScanResult(redisParser.parseBidsScanResult(
                "[[[{\"tag_key\": \"tag\", \"issues\":[{\"spec_name\":\"malicious_domain\",\"value\":\"ads.deceivenetworks.net\",\"first_adinstance\":\"e91e8da982bb8b7f80100426\"}]}]]]"));

        doReturn(Future.succeededFuture(bidsScanResult)).when(redisClient).submitBids(any());
        doReturn(AuctionContext.builder().build()).when(auctionInvocationContext).auctionContext();

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> future = hook.call(
                allProcessedBidResponsesPayload, auctionInvocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AllProcessedBidResponsesPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.update);
        assertThat(result.errors().get(0)).isEqualTo("tag: [Issue(specName=malicious_domain, value=ads.deceivenetworks.net, firstAdinstance=e91e8da982bb8b7f80100426)]");
        assertThat(result.debugMessages()).isNull();
    }

    @Test
    public void shouldSubmitBidsToRedisWhenScanIsNotDisabled() {
        // given
        final BidsScanResult bidsScanResult = new BidsScanResult(redisParser.parseBidsScanResult(
                "[[[{\"tag_key\": \"tag\", \"issues\":[{\"spec_name\":\"malicious_domain\",\"value\":\"ads.deceivenetworks.net\",\"first_adinstance\":\"e91e8da982bb8b7f80100426\"}]}]]]"));

        doReturn(false).when(redisScanStateChecker).isScanDisabled();
        doReturn(Future.succeededFuture(bidsScanResult)).when(redisClient).submitBids(any());
        doReturn(AuctionContext.builder().build()).when(auctionInvocationContext).auctionContext();

        // when
        hook.call(allProcessedBidResponsesPayload, auctionInvocationContext);

        // then
        verify(redisClient, times(1)).submitBids(any());
    }

    @Test
    public void shouldNotSubmitBidsToRedisWhenScanIsDisabled() {
        // given
        doReturn(true).when(redisScanStateChecker).isScanDisabled();
        doReturn(AuctionContext.builder().build()).when(auctionInvocationContext).auctionContext();

        // when
        hook.call(allProcessedBidResponsesPayload, auctionInvocationContext);

        // then
        verify(redisClient, never()).submitBids(any());
    }

    @Test
    public void shouldReturnResultWithDebugInfoWhenDebugIsEnabledAndRequestIsBroken() {
        // given
        final BidsScanResult bidsScanResult = new BidsScanResult(redisParser.parseBidsScanResult("[[[{\"t"));

        doReturn(Future.succeededFuture(bidsScanResult)).when(redisClient).submitBids(any());
        doReturn(true).when(auctionInvocationContext).debugEnabled();
        doReturn(AuctionContext.builder().build()).when(auctionInvocationContext).auctionContext();

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> future = hook.call(
                allProcessedBidResponsesPayload, auctionInvocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AllProcessedBidResponsesPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.errors()).isNull();
        assertThat(result.debugMessages().get(0)).isEqualTo("Error during parse redis response: [[[{\"t");
    }

    @Test
    public void shouldReturnResultWithoutDebugInfoWhenDebugIsDisabledAndRequestIsBroken() {
        // given
        final BidsScanResult bidsScanResult = new BidsScanResult(redisParser.parseBidsScanResult("[[[{\"t"));

        doReturn(Future.succeededFuture(bidsScanResult)).when(redisClient).submitBids(any());
        doReturn(false).when(auctionInvocationContext).debugEnabled();
        doReturn(AuctionContext.builder().build()).when(auctionInvocationContext).auctionContext();

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> future = hook.call(
                allProcessedBidResponsesPayload, auctionInvocationContext);

        // then
        assertThat(future).isNotNull();
        assertThat(future.succeeded()).isTrue();

        final InvocationResult<AllProcessedBidResponsesPayload> result = future.result();
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(InvocationStatus.success);
        assertThat(result.action()).isEqualTo(InvocationAction.no_action);
        assertThat(result.errors()).isNull();
        assertThat(result.debugMessages()).isNull();
    }
}
