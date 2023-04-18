package org.prebid.server.hooks.modules.com.confiant.adquality.v1;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.prebid.server.auction.model.BidderResponse;
import org.prebid.server.hooks.execution.v1.bidder.AllProcessedBidResponsesPayloadImpl;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.BidsMapper;
import org.prebid.server.hooks.modules.com.confiant.adquality.core.RedisClient;
import org.prebid.server.hooks.modules.com.confiant.adquality.model.BidsScanResult;
import org.prebid.server.hooks.modules.com.confiant.adquality.v1.model.InvocationResultImpl;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesHook;
import org.prebid.server.hooks.v1.bidder.AllProcessedBidResponsesPayload;
import org.prebid.server.hooks.v1.bidder.BidResponsesInvocationContext;

import java.util.List;

public class ConfiantAdQualityBidResponsesScanHook implements AllProcessedBidResponsesHook {

    private static final Logger logger = LoggerFactory.getLogger(ConfiantAdQualityBidResponsesScanHook.class);

    private static final String CODE = "confiant-ad-quality-bid-responses-scan-hook";

    private final RedisClient redisClient;

    public ConfiantAdQualityBidResponsesScanHook(RedisClient redisClient) {
        this.redisClient = redisClient;
    }

    @Override
    public Future<InvocationResult<AllProcessedBidResponsesPayload>> call(
            AllProcessedBidResponsesPayload allProcessedBidResponsesPayload,
            BidResponsesInvocationContext bidResponsesInvocationContext
    ) {
        logger.info("Hook -> confiant-ad-quality-hook");

        final BidRequest bidRequest = bidResponsesInvocationContext.bidRequest();
        final List<BidderResponse> responses = allProcessedBidResponsesPayload.bidResponses();
        final BidsScanResult bidsScanResult = redisClient
                .submitBids(BidsMapper.bidResponsesToRedisBids(bidRequest, responses));
        final boolean hasIssues = bidsScanResult.hasIssues();

        final InvocationResultImpl.InvocationResultImplBuilder<AllProcessedBidResponsesPayload> resultBuilder =
                InvocationResultImpl.<AllProcessedBidResponsesPayload>builder()
                        .status(InvocationStatus.success)
                        .action(hasIssues
                                ? InvocationAction.update
                                : InvocationAction.no_action)
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
