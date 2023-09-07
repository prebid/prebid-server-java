package org.prebid.server.hooks.modules.com.confiant.adquality.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.BidsMapper;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.BidsScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.BidsScanner;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisParser;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ConfiantAdQualityBidResponsesScanHookTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidsScanner bidsScanner;

    @Mock
    private AllProcessedBidResponsesPayload allProcessedBidResponsesPayload;

    @Mock
    private AuctionInvocationContext auctionInvocationContext;

    @Mock
    private ActivityInfrastructure activityInfrastructure;

    @Mock
    private PrivacyEnforcementService privacyEnforcementService;

    private ConfiantAdQualityBidResponsesScanHook hook;

    private final RedisParser redisParser = new RedisParser();

    @Before
    public void setUp() {
        hook = new ConfiantAdQualityBidResponsesScanHook(bidsScanner, privacyEnforcementService);
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

        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(getAuctionContext()).when(auctionInvocationContext).auctionContext();

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

        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(getAuctionContext()).when(auctionInvocationContext).auctionContext();

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
    public void shouldSubmitBidsToScan() {
        // given
        final BidsScanResult bidsScanResult = new BidsScanResult(redisParser.parseBidsScanResult(
                "[[[{\"tag_key\": \"tag\", \"issues\":[{\"spec_name\":\"malicious_domain\",\"value\":\"ads.deceivenetworks.net\",\"first_adinstance\":\"e91e8da982bb8b7f80100426\"}]}]]]"));

        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(getAuctionContext()).when(auctionInvocationContext).auctionContext();

        // when
        hook.call(allProcessedBidResponsesPayload, auctionInvocationContext);

        // then
        verify(bidsScanner, times(1)).submitBids(any());
    }

    @Test
    public void shouldSubmitBidsWithoutMaskedGeoInfoWhenTransmitGeoIsAllowed() {
        // given
        final Boolean transmitGeoIsAllowed = true;
        final BidsScanResult bidsScanResult = new BidsScanResult(redisParser.parseBidsScanResult(
                "[[[{\"tag_key\": \"tag\", \"issues\":[{\"spec_name\":\"malicious_domain\",\"value\":\"ads.deceivenetworks.net\",\"first_adinstance\":\"e91e8da982bb8b7f80100426\"}]}]]]"));
        final User user = privacyEnforcementService.maskUserConsideringActivityRestrictions(
                getUser(), true, !transmitGeoIsAllowed);
        final Device device = privacyEnforcementService.maskDeviceConsideringActivityRestrictions(
                getDevice(), true, !transmitGeoIsAllowed);

        bidsScanner.enableScan();
        doReturn(transmitGeoIsAllowed).when(activityInfrastructure).isAllowed(any(), any());
        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(getAuctionContext()).when(auctionInvocationContext).auctionContext();

        // when
        hook.call(allProcessedBidResponsesPayload, auctionInvocationContext);

        // then
        verify(bidsScanner, times(1)).submitBids(
                BidsMapper.toRedisBidsFromBidResponses(BidRequest.builder()
                        .user(user)
                        .device(device)
                        .build(), List.of())
        );
    }

    @Test
    public void shouldSubmitBidsWithMaskedGeoInfoWhenTransmitGeoIsNotAllowed() {
        // given
        final Boolean transmitGeoIsAllowed = false;
        final BidsScanResult bidsScanResult = new BidsScanResult(redisParser.parseBidsScanResult(
                "[[[{\"tag_key\": \"tag\", \"issues\":[{\"spec_name\":\"malicious_domain\",\"value\":\"ads.deceivenetworks.net\",\"first_adinstance\":\"e91e8da982bb8b7f80100426\"}]}]]]"));
        final User user = privacyEnforcementService.maskUserConsideringActivityRestrictions(
                getUser(), true, !transmitGeoIsAllowed);
        final Device device = privacyEnforcementService.maskDeviceConsideringActivityRestrictions(
                getDevice(), true, !transmitGeoIsAllowed);

        bidsScanner.enableScan();
        doReturn(transmitGeoIsAllowed).when(activityInfrastructure).isAllowed(any(), any());
        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(getAuctionContext()).when(auctionInvocationContext).auctionContext();

        // when
        hook.call(allProcessedBidResponsesPayload, auctionInvocationContext);

        // then
        verify(bidsScanner, times(1)).submitBids(
                BidsMapper.toRedisBidsFromBidResponses(BidRequest.builder()
                        .user(user)
                        .device(device)
                        .build(), List.of())
        );
    }

    @Test
    public void shouldReturnResultWithDebugInfoWhenDebugIsEnabledAndRequestIsBroken() {
        // given
        final BidsScanResult bidsScanResult = new BidsScanResult(redisParser.parseBidsScanResult("[[[{\"t"));

        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(true).when(auctionInvocationContext).debugEnabled();
        doReturn(getAuctionContext()).when(auctionInvocationContext).auctionContext();

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

        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(false).when(auctionInvocationContext).debugEnabled();
        doReturn(getAuctionContext()).when(auctionInvocationContext).auctionContext();

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

    private AuctionContext getAuctionContext() {
        return AuctionContext.builder()
                .activityInfrastructure(activityInfrastructure)
                .bidRequest(BidRequest.builder()
                        .user(getUser())
                        .device(getDevice())
                        .build())
                .build();
    }

    private User getUser() {
        return User.builder().geo(Geo.builder().country("country-u").region("region-u").build()).build();
    }

    private Device getDevice() {
        return Device.builder().geo(Geo.builder().country("country-d").region("region-d").build()).build();
    }
}
