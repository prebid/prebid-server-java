package org.prebid.server.hooks.modules.rule.engine.v1;

import com.iab.openrtb.request.BidRequest;
import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.InvocationResultImpl;
import org.prebid.server.hooks.execution.v1.analytics.TagsImpl;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.rule.engine.core.cache.RuleRegistry;
import org.prebid.server.hooks.modules.rule.engine.core.rules.PerStageRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.Rule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.RuleResult;
import org.prebid.server.hooks.v1.InvocationAction;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.InvocationStatus;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;
import org.prebid.server.model.UpdateResult;

import java.util.Collections;
import java.util.Objects;

public class RuleEngineProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "rule-engine-processed-auction-request";

    private final RuleRegistry ruleRegistry;

    public RuleEngineProcessedAuctionRequestHook(RuleRegistry ruleRegistry) {
        this.ruleRegistry = Objects.requireNonNull(ruleRegistry);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        final String accountId = invocationContext.auctionContext().getAccount().getId();
        return ruleRegistry.forAccount(accountId, invocationContext.accountConfig())
                .map(PerStageRule::processedAuctionRequestRule)
                .map(rule -> executeSafely(rule, auctionRequestPayload.bidRequest()))
                .map(RuleEngineProcessedAuctionRequestHook::succeeded)
                .recover(RuleEngineProcessedAuctionRequestHook::failure);
    }

    private static RuleResult<BidRequest> executeSafely(Rule<BidRequest> rule, BidRequest bidRequest) {
        return rule != null
                ? rule.process(bidRequest)
                : RuleResult.of(UpdateResult.unaltered(bidRequest), TagsImpl.of(Collections.emptyList()));
    }

    private static InvocationResult<AuctionRequestPayload> succeeded(RuleResult<BidRequest> result) {
        final UpdateResult<BidRequest> updateResult = result.getUpdateResult();

        return InvocationResultImpl.<AuctionRequestPayload>builder()
                .status(InvocationStatus.success)
                .action(updateResult.isUpdated() ? InvocationAction.update : InvocationAction.no_action)
                .payloadUpdate(initialPayload -> AuctionRequestPayloadImpl.of(updateResult.getValue()))
                .analyticsTags(result.getAnalyticsTags())
                .build();
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
