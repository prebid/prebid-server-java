package org.prebid.server.hooks.modules.com.confiant.adquality.v1;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Geo;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.bidder.model.BidderSeatBid;
import org.prebid.server.hooks.execution.v1.bidder.AllProcessedBidResponsesPayloadImpl;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.BidsMapper;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.BidsScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.BidsScanner;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisParser;
import org.prebid.server.hooks.modules.com.confiant.adquality.util.AdQualityModuleTestUtils;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.analytics.ActivityImpl;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.analytics.AppliedToImpl;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.analytics.ResultImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.PayloadUpdate;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesPayload;

import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class ConfiantAdQualityBidResponsesScanHookTest {

    @Mock
    private BidsScanner bidsScanner;

    @Mock
    private AllProcessedBidResponsesPayload allProcessedBidResponsesPayload;

    @Mock
    private AuctionInvocationContext auctionInvocationContext;

    @Mock
    private ActivityInfrastructure activityInfrastructure;

    @Mock
    private UserFpdActivityMask userFpdActivityMask;

    private ConfiantAdQualityBidResponsesScanHook target;

    private final RedisParser redisParser = new RedisParser(new ObjectMapper());

    @BeforeEach
    public void setUp() {
        target = new ConfiantAdQualityBidResponsesScanHook(bidsScanner, List.of(), userFpdActivityMask);
    }

    @Test
    public void codeShouldHaveValidConfigsWhenInitialized() {
        // given

        // when

        // then
        assertThat(target.code()).isEqualTo("confiant-ad-quality-bid-responses-scan-hook");
    }

    @Test
    public void callShouldReturnResultWithNoActionWhenRedisHasNoAnswer() {
        // given
        final BidsScanResult bidsScanResult = BidsScanResult.builder()
                .bidScanResults(Collections.emptyList())
                .debugMessages(Collections.emptyList())
                .build();

        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(getAuctionContext()).when(auctionInvocationContext).auctionContext();

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> future = target.call(
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
    public void callShouldReturnResultWithUpdateActionWhenRedisHasFoundSomeIssues() {
        // given
        final BidsScanResult bidsScanResult = redisParser.parseBidsScanResult(
                "[[[{\"tag_key\": \"tag\", \"issues\":[{\"spec_name\":\"malicious_domain\",\"value\":\"ads.deceivenetworks.net\",\"first_adinstance\":\"e91e8da982bb8b7f80100426\"}]}]]]");

        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(getAuctionContext()).when(auctionInvocationContext).auctionContext();
        doReturn(List.of(AdQualityModuleTestUtils.getBidderResponse("bidder_a", "imp_a", "bid_id_a")))
                .when(allProcessedBidResponsesPayload).bidResponses();

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> future = target.call(
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
        assertThat(result.analyticsTags().activities()).isEqualTo(singletonList(ActivityImpl.of(
                "ad-scan", "success", List.of(
                        ResultImpl.of("inspected-has-issue", null, AppliedToImpl.builder()
                                .bidders(List.of("bidder_a"))
                                .impIds(List.of("imp_a"))
                                .bidIds(List.of("bid_id_a"))
                                .build()))
        )));
    }

    @Test
    public void callShouldSubmitBidsToScanWhenBidsCome() {
        // given
        final BidsScanResult bidsScanResult = redisParser.parseBidsScanResult(
                "[[[{\"tag_key\": \"tag\", \"issues\":[{\"spec_name\":\"malicious_domain\",\"value\":\"ads.deceivenetworks.net\",\"first_adinstance\":\"e91e8da982bb8b7f80100426\"}]}]]]");

        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(getAuctionContext()).when(auctionInvocationContext).auctionContext();

        // when
        target.call(allProcessedBidResponsesPayload, auctionInvocationContext);

        // then
        verify(bidsScanner).submitBids(any());
    }

    @Test
    public void callShouldSubmitToScanBidsWhichAreNotPartOfTheExcludeToScanListWhenHookIsConfiguredWithExcludeToScanList() {
        // given
        final String secureBidderName = "securebidder";
        final String notSecureBadBidderName = "notsecurebadbidder";
        final String notSecureGoodBidderName = "notsecuregoodbidder";
        final BidderResponse secureBidderResponse = AdQualityModuleTestUtils.getBidderResponse(secureBidderName, "imp_a", "bid_id_a");
        final BidderResponse notSecureBadBidderResponse = AdQualityModuleTestUtils.getBidderResponse(notSecureBadBidderName, "imp_b", "bid_id_b");
        final BidderResponse notSecureGoodBidderResponse = AdQualityModuleTestUtils.getBidderResponse(notSecureGoodBidderName, "imp_c", "bid_id_c");
        final BidsScanResult bidsScanResult = redisParser.parseBidsScanResult(
                "[[[{\"tag_key\": \"tag\", \"issues\":[{\"spec_name\":\"malicious_domain\",\"value\":\"ads.deceivenetworks.net\",\"first_adinstance\":\"e91e8da982bb8b7f80100426\"}]}]],[[{\"tag_key\": \"key_b\", \"imp_id\": \"imp_b\", \"issues\": []}]]]]");
        final AuctionContext auctionContext = AuctionContext.builder()
                .activityInfrastructure(activityInfrastructure)
                .bidRequest(BidRequest.builder().cur(List.of("USD")).build())
                .build();

        target = new ConfiantAdQualityBidResponsesScanHook(bidsScanner, List.of(secureBidderName), userFpdActivityMask);

        doReturn(List.of(secureBidderResponse, notSecureBadBidderResponse, notSecureGoodBidderResponse)).when(allProcessedBidResponsesPayload).bidResponses();
        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(auctionContext).when(auctionInvocationContext).auctionContext();

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> invocationResult = target
                .call(allProcessedBidResponsesPayload, auctionInvocationContext);

        // then
        verify(bidsScanner).submitBids(
                BidsMapper.toRedisBidsFromBidResponses(auctionContext.getBidRequest(), List.of(notSecureBadBidderResponse, notSecureGoodBidderResponse))
        );

        final PayloadUpdate<AllProcessedBidResponsesPayload> payloadUpdate = invocationResult.result().payloadUpdate();
        final AllProcessedBidResponsesPayloadImpl initPayloadToUpdate = AllProcessedBidResponsesPayloadImpl.of(
                asList(secureBidderResponse, notSecureBadBidderResponse, notSecureGoodBidderResponse));
        final AllProcessedBidResponsesPayloadImpl resultPayloadAfterUpdate = AllProcessedBidResponsesPayloadImpl.of(
                asList(notSecureGoodBidderResponse, secureBidderResponse));

        assertThat(payloadUpdate.apply(initPayloadToUpdate)).isEqualTo(resultPayloadAfterUpdate);
        assertThat(invocationResult.result().analyticsTags().activities()).isEqualTo(singletonList(ActivityImpl.of(
                "ad-scan", "success", List.of(
                        ResultImpl.of("skipped", null, AppliedToImpl.builder()
                                .bidders(List.of(secureBidderName))
                                .impIds(List.of("imp_a"))
                                .bidIds(List.of("bid_id_a"))
                                .build()),
                        ResultImpl.of("inspected-has-issue", null, AppliedToImpl.builder()
                                .bidders(List.of(notSecureBadBidderName))
                                .impIds(List.of("imp_b"))
                                .bidIds(List.of("bid_id_b"))
                                .build()),
                        ResultImpl.of("inspected-no-issues", null, AppliedToImpl.builder()
                                .bidders(List.of(notSecureGoodBidderName))
                                .impIds(List.of("imp_c"))
                                .bidIds(List.of("bid_id_c"))
                                .build()))
        )));
    }

    @Test
    public void callShouldSubmitToScanOnlyBidsWithDataWhenSomeBiddersRespondWithEmptyResponse() {
        // given
        final String secureBidderName = "securebidder";
        final String notSecureBadBidderName = "notsecurebadbidder";
        final String emptyBidderName = "emptybidder";
        final BidderResponse secureBidderResponse = AdQualityModuleTestUtils.getBidderResponse(secureBidderName, "imp_a", "bid_id_a");
        final BidderResponse notSecureBadBidderResponse = AdQualityModuleTestUtils.getBidderResponse(notSecureBadBidderName, "imp_b", "bid_id_b");
        final BidderResponse emptyBidderResponse = getEmptyBidderResponse(emptyBidderName);
        final BidsScanResult bidsScanResult = redisParser.parseBidsScanResult(
                "[[[{\"tag_key\": \"tag\", \"issues\":[{\"spec_name\":\"malicious_domain\",\"value\":\"ads.deceivenetworks.net\",\"first_adinstance\":\"e91e8da982bb8b7f80100426\"}]}]]]");
        final AuctionContext auctionContext = AuctionContext.builder()
                .activityInfrastructure(activityInfrastructure)
                .bidRequest(BidRequest.builder().cur(List.of("USD")).build())
                .build();

        target = new ConfiantAdQualityBidResponsesScanHook(bidsScanner, List.of(secureBidderName), userFpdActivityMask);

        doReturn(List.of(secureBidderResponse, notSecureBadBidderResponse, emptyBidderResponse)).when(allProcessedBidResponsesPayload).bidResponses();
        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(auctionContext).when(auctionInvocationContext).auctionContext();

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> invocationResult = target
                .call(allProcessedBidResponsesPayload, auctionInvocationContext);

        // then
        verify(bidsScanner).submitBids(
                BidsMapper.toRedisBidsFromBidResponses(auctionContext.getBidRequest(), List.of(notSecureBadBidderResponse))
        );

        final PayloadUpdate<AllProcessedBidResponsesPayload> payloadUpdate = invocationResult.result().payloadUpdate();
        final AllProcessedBidResponsesPayloadImpl initPayloadToUpdate = AllProcessedBidResponsesPayloadImpl.of(
                asList(secureBidderResponse, notSecureBadBidderResponse, emptyBidderResponse));
        final AllProcessedBidResponsesPayloadImpl resultPayloadAfterUpdate = AllProcessedBidResponsesPayloadImpl.of(
                asList(secureBidderResponse, emptyBidderResponse));

        assertThat(payloadUpdate.apply(initPayloadToUpdate)).isEqualTo(resultPayloadAfterUpdate);
        assertThat(invocationResult.result().analyticsTags().activities()).isEqualTo(singletonList(ActivityImpl.of(
                "ad-scan", "success", List.of(
                        ResultImpl.of("skipped", null, AppliedToImpl.builder()
                                .bidders(List.of(secureBidderName, emptyBidderName))
                                .impIds(List.of("imp_a"))
                                .bidIds(List.of("bid_id_a"))
                                .build()),
                        ResultImpl.of("inspected-has-issue", null, AppliedToImpl.builder()
                                .bidders(List.of(notSecureBadBidderName))
                                .impIds(List.of("imp_b"))
                                .bidIds(List.of("bid_id_b"))
                                .build()))
        )));
    }

    @Test
    public void callShouldSubmitBidsWithoutMaskedGeoInfoWhenTransmitGeoIsAllowed() {
        // given
        final Boolean transmitGeoIsAllowed = true;
        final BidsScanResult bidsScanResult = redisParser.parseBidsScanResult(
                "[[[{\"tag_key\": \"tag\", \"issues\":[{\"spec_name\":\"malicious_domain\",\"value\":\"ads.deceivenetworks.net\",\"first_adinstance\":\"e91e8da982bb8b7f80100426\"}]}]]]");
        final User user = userFpdActivityMask.maskUser(
                getUser(), true, true, !transmitGeoIsAllowed);
        final Device device = userFpdActivityMask.maskDevice(
                getDevice(), true, !transmitGeoIsAllowed);

        bidsScanner.enableScan();
        doReturn(transmitGeoIsAllowed).when(activityInfrastructure).isAllowed(any(), any());
        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(getAuctionContext()).when(auctionInvocationContext).auctionContext();

        // when
        target.call(allProcessedBidResponsesPayload, auctionInvocationContext);

        // then
        verify(bidsScanner).submitBids(
                BidsMapper.toRedisBidsFromBidResponses(BidRequest.builder()
                        .user(user)
                        .device(device)
                        .cur(List.of("USD"))
                        .build(), List.of())
        );
    }

    @Test
    public void callShouldSubmitBidsWithMaskedGeoInfoWhenTransmitGeoIsNotAllowed() {
        // given
        final Boolean transmitGeoIsAllowed = false;
        final BidsScanResult bidsScanResult = redisParser.parseBidsScanResult(
                "[[[{\"tag_key\": \"tag\", \"issues\":[{\"spec_name\":\"malicious_domain\",\"value\":\"ads.deceivenetworks.net\",\"first_adinstance\":\"e91e8da982bb8b7f80100426\"}]}]]]");
        final User user = userFpdActivityMask.maskUser(
                getUser(), true, true, !transmitGeoIsAllowed);
        final Device device = userFpdActivityMask.maskDevice(
                getDevice(), true, !transmitGeoIsAllowed);

        bidsScanner.enableScan();
        doReturn(transmitGeoIsAllowed).when(activityInfrastructure).isAllowed(any(), any());
        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(getAuctionContext()).when(auctionInvocationContext).auctionContext();

        // when
        target.call(allProcessedBidResponsesPayload, auctionInvocationContext);

        // then
        verify(bidsScanner).submitBids(
                BidsMapper.toRedisBidsFromBidResponses(BidRequest.builder()
                        .user(user)
                        .device(device)
                        .cur(List.of("USD"))
                        .build(), List.of())
        );
    }

    @Test
    public void callShouldReturnResultWithDebugInfoWhenDebugIsEnabledAndRequestIsBroken() {
        // given
        final BidsScanResult bidsScanResult = redisParser.parseBidsScanResult("[[[{\"t");

        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(true).when(auctionInvocationContext).debugEnabled();
        doReturn(getAuctionContext()).when(auctionInvocationContext).auctionContext();

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> future = target.call(
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
    public void callShouldReturnResultWithoutDebugInfoWhenDebugIsDisabledAndRequestIsBroken() {
        // given
        final BidsScanResult bidsScanResult = redisParser.parseBidsScanResult("[[[{\"t");

        doReturn(Future.succeededFuture(bidsScanResult)).when(bidsScanner).submitBids(any());
        doReturn(false).when(auctionInvocationContext).debugEnabled();
        doReturn(getAuctionContext()).when(auctionInvocationContext).auctionContext();

        // when
        final Future<InvocationResult<AllProcessedBidResponsesPayload>> future = target.call(
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
                        .cur(List.of("USD"))
                        .build())
                .build();
    }

    private static User getUser() {
        return User.builder().geo(Geo.builder().country("country-u").region("region-u").build()).build();
    }

    private static Device getDevice() {
        return Device.builder().geo(Geo.builder().country("country-d").region("region-d").build()).build();
    }

    private static BidderResponse getEmptyBidderResponse(String bidderName) {
        return BidderResponse.of(bidderName, BidderSeatBid.builder()
                .bids(Collections.emptyList())
                .build(), 5);
    }
}
