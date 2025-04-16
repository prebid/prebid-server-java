package org.prebid.server.hooks.modules.rule.engine.v1;

import io.vertx.core.Future;
import org.prebid.server.hooks.execution.v1.auction.AuctionRequestPayloadImpl;
import org.prebid.server.hooks.modules.rule.engine.core.RuleParser;
import org.prebid.server.hooks.modules.rule.engine.core.rules.request.RequestRule;
import org.prebid.server.hooks.modules.rule.engine.core.rules.request.RequestRuleResult;
import org.prebid.server.hooks.v1.InvocationResult;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.hooks.v1.auction.ProcessedAuctionRequestHook;

import java.util.Objects;

public class RuleEngineProcessedAuctionRequestHook implements ProcessedAuctionRequestHook {

    private static final String CODE = "rule-engine-processed-auction-request";

    private final RuleParser ruleParser;

    public RuleEngineProcessedAuctionRequestHook(RuleParser ruleParser) {
        this.ruleParser = Objects.requireNonNull(ruleParser);
    }

    @Override
    public Future<InvocationResult<AuctionRequestPayload>> call(AuctionRequestPayload auctionRequestPayload,
                                                                AuctionInvocationContext invocationContext) {

        final RequestRule rule = ruleParser.parse(invocationContext.accountConfig());
        final RequestRuleResult result = rule.process(auctionRequestPayload.bidRequest(), false);

        return Future.succeededFuture(succeeded(payload ->
                AuctionRequestPayloadImpl.of(payload.bidRequest().toBuilder()
                        .ext(originalBidRequest.getExt())
                        .build())));
    }

    @Override
    public String code() {
        return CODE;
    }
}
