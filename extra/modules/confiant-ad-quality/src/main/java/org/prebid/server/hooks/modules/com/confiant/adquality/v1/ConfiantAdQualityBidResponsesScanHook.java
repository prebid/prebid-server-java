package org.prebid.server.hooks.modules.com.confiant.adquality.v1;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.hooks.execution.v1.bidder.AllProcessedBidResponsesPayloadImpl;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.BidsMapper;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.BidsScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisClient;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisScanStateChecker;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.OperationResult;
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

    private final RedisClient redisClient;

    private final RedisScanStateChecker redisScanStateChecker;

    public ConfiantAdQualityBidResponsesScanHook(
            RedisClient redisClient,
            RedisScanStateChecker redisScanStateChecker
    ) {
        this.redisClient = redisClient;
        this.redisScanStateChecker = redisScanStateChecker;
    }

    @Override
    public Future<InvocationResult<AllProcessedBidResponsesPayload>> call(
            AllProcessedBidResponsesPayload allProcessedBidResponsesPayload,
            AuctionInvocationContext auctionInvocationContext
    ) {
        final BidRequest bidRequest = auctionInvocationContext.bidRequest();
        final List<BidderResponse> responses = allProcessedBidResponsesPayload.bidResponses();
        final boolean isScanDisabled = redisScanStateChecker.isScanDisabled();

        final BidsScanResult bidsScanResult = isScanDisabled
            ? new BidsScanResult(OperationResult.empty())
            : redisClient.submitBids(BidsMapper.bidResponsesToRedisBids(bidRequest, responses));

        final boolean hasIssues = !isScanDisabled && bidsScanResult.hasIssues();
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

        return Future.succeededFuture(resultBuilder.build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
