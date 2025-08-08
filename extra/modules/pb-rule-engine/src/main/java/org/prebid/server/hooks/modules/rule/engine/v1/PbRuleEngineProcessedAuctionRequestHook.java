package org.prebid.server.hooks.modules.rule.engine.v1;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.rule.engine.core.config.RuleParser;
import org.prebid.server.hooks.modules.rule.engine.core.request.Granularity;
import org.prebid.server.hooks.modules.rule.engine.core.request.RequestRuleContext;
import org.prebid.server.hooks.modules.rule.engine.core.rules.PerStageRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.model.UpdateResult;

import java.util.Objects;

public class PbRuleEngineProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "pb-rule-engine-processed-auction-request";

    private final RuleParser ruleParser;
    private final String datacenter;

    public PbRuleEngineProcessedAuctionRequestHook(RuleParser ruleParser, String datacenter) {
        this.ruleParser = Objects.requireNonNull(ruleParser);
        this.datacenter = Objects.requireNonNull(datacenter);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        final AuctionContext context = invocationContext.auctionContext();
        final String accountId = StringUtils.defaultString(invocationContext.auctionContext().getAccount().getId());
        final ObjectNode accountConfig = invocationContext.accountConfig();
        final BidRequest bidRequest = auctionRequestPayload.bidRequest();

        if (accountConfig == null) {
            return succeeded(RuleResult.unaltered(bidRequest));
        }

        return ruleParser.parseForAccount(accountId, accountConfig)
                .map(PerStageRule::processedAuctionRequestRule)
                .map(rule -> rule.process(
                        bidRequest, RequestRuleContext.of(context, Granularity.Request.instance(), datacenter)))
                .flatMap(PbRuleEngineProcessedAuctionRequestHook::succeeded)
                .recover(PbRuleEngineProcessedAuctionRequestHook::failure);
    }

    private static Future<InvocationResult<AuctionRequestPayload>> succeeded(RuleResult<BidRequest> result) {
        final UpdateResult<BidRequest> updateResult = result.getUpdateResult();

        final InvocationResult<AuctionRequestPayload> invocationResult =
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.success)
                        .action(updateResult.isUpdated() ? InvocationAction.update : InvocationAction.no_action)
                        .payloadUpdate(initialPayload -> AuctionRequestPayloadImpl.of(updateResult.getValue()))
                        .analyticsTags(result.getAnalyticsTags())
                        .build();

        return Future.succeededFuture(invocationResult);
    }

    private static Future<InvocationResult<AuctionRequestPayload>> failure(Throwable error) {
        return Future.succeededFuture(
                InvocationResultImpl.<AuctionRequestPayload>builder()
                        .status(InvocationStatus.failure)
                        .action(InvocationAction.no_invocation)
                        .message(error.getMessage())
                        .build());
    }

    @Override
    public String code() {
        return CODE;
    }
}
