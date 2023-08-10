package org.prebid.server.hooks.modules.com.confiant.adquality.v1;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.payload.ActivityCallPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityCallPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.BidRequestActivityCallPayload;
import org.prebid.server.auction.PrivacyEnforcementService;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.hooks.execution.v1.bidder.AllProcessedBidResponsesPayloadImpl;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.BidsMapper;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.BidsScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.BidsScanner;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.InvocationResultImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesHook;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesPayload;

import java.util.List;

public class ConfiantAdQualityBidResponsesScanHook implements AllProcessedBidResponsesHook {

    private static final String CODE = "confiant-ad-quality-bid-responses-scan-hook";

    private final BidsScanner bidsScanner;

    private final PrivacyEnforcementService privacyEnforcementService;

    public ConfiantAdQualityBidResponsesScanHook(
            BidsScanner bidsScanner,
            PrivacyEnforcementService privacyEnforcementService) {
        this.bidsScanner = bidsScanner;
        this.privacyEnforcementService = privacyEnforcementService;
    }

    @Override
    public Future<InvocationResult<AllProcessedBidResponsesPayload>> call(
            AllProcessedBidResponsesPayload allProcessedBidResponsesPayload,
            AuctionInvocationContext auctionInvocationContext) {
        final BidRequest bidRequest = getBidRequest(auctionInvocationContext);
        final List<BidderResponse> responses = allProcessedBidResponsesPayload.bidResponses();

        return bidsScanner.submitBids(BidsMapper.toRedisBidsFromBidResponses(bidRequest, responses))
                .map(scanResult -> toInvocationResult(scanResult, auctionInvocationContext));
    }

    private BidRequest getBidRequest(AuctionInvocationContext auctionInvocationContext) {
        final AuctionContext auctionContext = auctionInvocationContext.auctionContext();
        final BidRequest bidRequest = auctionContext.getBidRequest();
        final ActivityCallPayload activityCallPayload = BidRequestActivityCallPayload.of(
                ActivityCallPayloadImpl.of(ComponentType.GENERAL_MODULE, ConfiantAdQualityModule.CODE),
                bidRequest);
        final boolean disallowTransmitGeo = !auctionContext.getActivityInfrastructure()
                .isAllowed(Activity.TRANSMIT_GEO, activityCallPayload);

        final User maskedUser = privacyEnforcementService
                .maskUserConsideringActivityRestrictions(bidRequest.getUser(), true, disallowTransmitGeo);
        final Device maskedDevice = privacyEnforcementService
                .maskDeviceConsideringActivityRestrictions(bidRequest.getDevice(), true, disallowTransmitGeo);

        return bidRequest.toBuilder()
                .user(maskedUser)
                .device(maskedDevice)
                .build();
    }

    private InvocationResult<AllProcessedBidResponsesPayload> toInvocationResult(
            BidsScanResult bidsScanResult,
            AuctionInvocationContext auctionInvocationContext) {
        final boolean hasIssues = bidsScanResult.hasIssues();
        final boolean debugEnabled = auctionInvocationContext.debugEnabled();

        final InvocationResultImpl.InvocationResultImplBuilder<AllProcessedBidResponsesPayload> resultBuilder =
                InvocationResultImpl.<AllProcessedBidResponsesPayload>builder()
                        .status(InvocationStatus.success)
                        .action(hasIssues
                                ? InvocationAction.update
                                : InvocationAction.no_action)
                        .errors(hasIssues
                                ? bidsScanResult.getIssuesMessages()
                                : null)
                        .debugMessages(debugEnabled
                                ? bidsScanResult.getDebugMessages()
                                : null)
                        .payloadUpdate(payload -> hasIssues
                                ? AllProcessedBidResponsesPayloadImpl.of(bidsScanResult.filterValidResponses(payload.bidResponses()))
                                : AllProcessedBidResponsesPayloadImpl.of(payload.bidResponses()));

        return resultBuilder.build();
    }

    @Override
    public String code() {
        return CODE;
    }
}
